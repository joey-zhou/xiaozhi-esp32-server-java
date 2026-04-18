package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;

import lombok.Builder;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
/**
 * 实现对话摘要
 * @see org.springframework.ai.chat.memory.MessageWindowChatMemory
 * @see # org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor
 *
 * 设计的基本假设：
 * 1. 对聊天记录的成本因素考虑权重远远大于可靠性的考虑权重；
 * 2. 满足上述成本要求的前提下，尽量保证聊天的质量效果。
 *
 * 未来可考虑通过自定义一个Advisor向Prompt注入SystemMessage。
 * 这里约定 messages里不存放SystemMessage，它的模板，是作为单独一个变量记录。
 *
 * Conversation是从哪里来的？
 * 1. 用户与大模型的新对话，创建一个未入库的对话。这是最初的Conversation.
 * 2. 从库里初始化出来。这是一个已入库的Conversation。
 *
 * 从成本来看，大模型显卡算力成本 >> 存储IO成本 > 内存占用成本 > 存储空间成本 > CPU成本。
 * 为了对话的意思连贯，所有不在Prompt里的消息，都应该进行摘要处理。而摘要需要大模型调用，所以不能每条消息一次摘要，需要批量消息摘要。
 * @author Able
 */

@Slf4j
public class SummaryConversation extends Conversation {
    private static final int CONVERSATION_INTERVAL_HOURS = 1;
    private static final int DEFAULT_MAX_MESSAGES = 24;
    private static final int DEFAULT_BATCH_SIZE = 20;
    private final PromptTemplate initSummarizerPromptTemplate ;
    private final PromptTemplate againSummarizerPromptTemplate ;
    private final ChatMemory chatMemory;
    private final ChatModel chatModel;
    private final Object summaryLock = new Object();
    // 运行时不应该发生变化，避免计算错误

    private final int maxMessages ;
    // 运行时不应该发生变化，避免计算错误
    private final int batchSize;

    // 消息摘要
    private SummaryBO lastSummary = null;
    private boolean summarizing = false;

    @Builder
    public SummaryConversation(String ownerId, Integer roleId, String sessionId, String roleDesc, Integer userId,
                               PromptTemplate initSummarizerPromptTemplate, PromptTemplate againSummarizerPromptTemplate,
                               ChatMemory chatMemory, ChatModel chatModel, int maxMessages, int batchSize){
        super(ownerId, roleId, sessionId, roleDesc, userId);
        Assert.notNull(initSummarizerPromptTemplate, "initSummarizerPromptTemplate must not be null");
        this.initSummarizerPromptTemplate = initSummarizerPromptTemplate;

        Assert.notNull(againSummarizerPromptTemplate, "againSummarizerPromptTemplate must not be null");
        this.againSummarizerPromptTemplate = againSummarizerPromptTemplate;

        Assert.state(maxMessages>0, "maxMessages must be greater than 0");
        this.maxMessages = maxMessages;

        Assert.state(batchSize>0, "batchSize must be greater than 0");
        this.batchSize = batchSize;

        Assert.notNull(chatMemory, "chatMemory must not be null");
        this.chatMemory = chatMemory;

        Assert.notNull(chatModel, "chatModel must not be null");
        this.chatModel = chatModel;

        // 在新建Conversation时，可以加载以前的已有的Summary。
        this.lastSummary = chatMemory.findLastSummary(getOwnerId(), getRoleId());
        if(lastSummary == null){
            List<Message> history = chatMemory.find(getOwnerId(), getRoleId(), maxMessages);
            log.info("当前{}还没有历史summary,加载{}条普通消息进入对话上下文", getOwnerId(), history.size());
            synchronized (summaryLock) {
                super.messages.addAll(history);
            }
            // 如果最后一条消息距今超过1小时且消息数足够，则生成summary以压缩上下文
            if (history.size() >= 2) {
                Instant lastMessageTime = MessageTimeMetadata.getTimeMillis(history.getLast());
                if (Duration.between(lastMessageTime, Instant.now()).toHours() >= CONVERSATION_INTERVAL_HOURS) {
                    log.info("{}的最后一条消息已超过{}小时，生成summary压缩上下文", getOwnerId(), CONVERSATION_INTERVAL_HOURS);
                    summarize(true);
                }
            }
        }else {
            List<Message> history = chatMemory.find(getOwnerId(), getRoleId(), lastSummary.getLastMessageTimestamp());
            log.info("加载{}的{}条未被摘要的消息作为对话历史", getOwnerId(), history.size());
            synchronized (summaryLock) {
                super.messages.addAll(history);
            }
            if (Duration.between(lastSummary.getLastMessageTimestamp(), Instant.now()).toHours() >= CONVERSATION_INTERVAL_HOURS
                    && history.size() >= 2) {
                log.info("{}的last summary已超过1小时，但还有一些剩余消息没有summarize,重新生成summary", getOwnerId());
                summarize(true);
            }
        }
    }

    /**
     * 添加消息
     * 后续考虑：继承封装UserMessage和AssistantMessage,UserMessageWithTime,AssistantMessageWithTime
     * @param message
     */
    @Override
    public void add(Message message) {
        synchronized (summaryLock) {
            super.add(message);
        }
        // 达到阈值则触发大模型进行摘要。只在添加 AssistantMessage 时触发（避免重复触发）
        if (message instanceof AssistantMessage) {
            summarize();
        }
    }

    private void summarize() {
        summarize(false);
    }

    private void summarize(boolean force) {
        List<Message> needSummaryMessages;
        int size;
        int actualBatchSize;
        synchronized (summaryLock) {
            if (summarizing) {
                return;
            }
            size = messages.size();
            if (size == 0 || (!force && size < maxMessages)) {
                return;
            }
            actualBatchSize = Math.min(batchSize, size);
            if (actualBatchSize <= 0) {
                return;
            }
            needSummaryMessages = new ArrayList<>(messages.subList(0, actualBatchSize));
            summarizing = true;
        }
        log.info("current conversation message size:{}, batch size to summary:{}", size, actualBatchSize);
        Thread.startVirtualThread(() -> summaryMessages(needSummaryMessages));
    }

    protected void summaryMessages(List<Message> needSummaryMessages) {
        // 1. Process memory messages as a string.
        String memory = needSummaryMessages.stream()
                .map(m -> {
                    if (m.getMessageType() == MessageType.TOOL) {
                        return "TOOL:" + m.getText();
                    }
                    if (m.getMessageType() == MessageType.ASSISTANT && m instanceof AssistantMessage am
                            && am.getToolCalls() != null && !am.getToolCalls().isEmpty()) {
                        String toolNames = am.getToolCalls().stream()
                                .map(tc -> tc.name())
                                .collect(Collectors.joining(","));
                        return "ASSISTANT:[tool_call:" + toolNames + "]";
                    }
                    return m.getMessageType() + ":" + m.getText();
                })
                .collect(Collectors.joining(System.lineSeparator()));

        // 2. 拼接提示词
        String lastSummaryText;
        synchronized (summaryLock) {
            lastSummaryText = lastSummary == null ? null : lastSummary.getSummary();
        }
        String factExtractPrompt;
        if (StringUtils.hasText(lastSummaryText)) {
            factExtractPrompt = againSummarizerPromptTemplate.render(Map.of(
                    "last_summary", lastSummaryText,
                    "conversation", memory
            ));
        } else {
            factExtractPrompt = initSummarizerPromptTemplate.render(Map.of(
                    "datetime", LocalDate.now().toString(),
                    "conversation", memory
            ));
        }

        try {
            // 3. Call the model.
            log.info("调用大模型进行摘要：{}", factExtractPrompt);

            String factExtract = chatModel.call(factExtractPrompt);
            log.info("大模型从对话里提取用户重要备忘: {}", factExtract);

            // 4. 入库存储。
            SummaryBO newSummary = new SummaryBO()
                    .setDeviceId(getOwnerId())
                    .setRoleId(getRoleId())
                    .setLastMessageTimestamp(MessageTimeMetadata.getTimeMillis(needSummaryMessages.getLast()).truncatedTo(ChronoUnit.SECONDS))
                    .setSummary(factExtract)
                    .setCreateTime(Instant.now());
            chatMemory.save(newSummary);

            synchronized (summaryLock) {
                // 5. 移除已处理的消息
                messages.removeAll(needSummaryMessages);
                this.lastSummary = newSummary;
                summarizing = false;
            }
            summarize();
        } catch (Exception e) {
            log.error("{}对话摘要失败", getOwnerId(), e);
            synchronized (summaryLock) {
                summarizing = false;
            }
        }
    }

    public List<Message> messages(ConversationContext context) {
        List<Message> messageSnapshot;
        SummaryBO summarySnapshot;
        synchronized (summaryLock) {
            messageSnapshot = new ArrayList<>(messages);
            summarySnapshot = lastSummary;
        }
        // 新消息列表对象，避免使用过程中污染原始列表对象
        List<Message> historyMessages = new ArrayList<>();
        var roleSystemMessage = roleSystemMessage(context);
        if(roleSystemMessage.isPresent()){
            historyMessages.add(roleSystemMessage.get());
        }
        if(summarySnapshot != null && StringUtils.hasText(summarySnapshot.getSummary())){
            // 多条SystemMessage在主流模型（OpenAI、Qwen、DeepSeek）中均已验证可用
            historyMessages.add(new SystemMessage("下面是你与用户最近聊天内容的摘要：\n" + summarySnapshot.getSummary()));
        }
        historyMessages.addAll(messageSnapshot);
        return historyMessages;
    }

    @Override
    public List<Message> messages() {
        return messages(ConversationContext.EMPTY);
    }
}

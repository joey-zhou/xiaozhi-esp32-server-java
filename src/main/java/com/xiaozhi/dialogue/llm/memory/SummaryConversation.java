package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.dialogue.service.Dialogue;
import com.xiaozhi.entity.SysSummary;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;

import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class SummaryConversation extends Conversation {
    private static final int CONVERSATION_INTERVAL_HOURS = 1;
    private static final int DEFAULT_MAX_MESSAGES = 24;
    private static final int DEFAULT_BATCH_SIZE = 20;
    private final Logger logger = LoggerFactory.getLogger(SummaryConversation.class);
    private final PromptTemplate initSummarizerPromptTemplate ;
    private final PromptTemplate againSummarizerPromptTemplate ;
    private final ChatMemory chatMemory;
    private final ChatModel chatModel;
    // 运行时不应该发生变化，避免计算错误

    private final int maxMessages ;
    // 运行时不应该发生变化，避免计算错误
    private final int batchSize;

    // 消息摘要
    private SysSummary lastSummary =null;

    @Builder
    public SummaryConversation(SysDevice device, SysRole role, String sessionId, PromptTemplate initSummarizerPromptTemplate,PromptTemplate againSummarizerPromptTemplate, ChatMemory chatMemory, ChatModel chatModel, int maxMessages, int batchSize){
        super(device, role, sessionId);
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
        this.lastSummary = chatMemory.findLastSummary(device().getDeviceId(), role().getRoleId());
        if(lastSummary == null){
            List<Message> history = chatMemory.find(device().getDeviceId(), role().getRoleId(), maxMessages);
            logger.info("当前设备{}还没有历史summary,加载{}条普通消息进入对话上下文", device().getDeviceId(), history.size());
            super.messages.addAll(history) ;
            // TODO 需要判断最后一条消息的时间差。如果超过1小时，则重新生成summary。
        }else {
            List<Message> history = chatMemory.find(device().getDeviceId(), role().getRoleId(), lastSummary.getLastMessageTimestamp());
            logger.info("加载设备{}的{}条未被摘要的普通消息(SysMessage.MESSAGE_TYPE_NORMAL)作为对话历史", device().getDeviceId(), history.size());
            super.messages.addAll(history);
            if (Duration.between(lastSummary.getLastMessageTimestamp(), Instant.now()).toHours() >= CONVERSATION_INTERVAL_HOURS
                    && history.size() >= 2) {
                logger.info("设备{}的last summary已超过1小时，但还有一些剩余消息没有summarize,重新生成summary", device().getDeviceId());
                summarize();
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
        super.add(message);
        // 达到阈值则触发大模型进行摘要。如果只是添加了UserMesassage，则暂时不需要急着摘要。
        if (message instanceof AssistantMessage  && messages.size() >= maxMessages) {
            summarize();
        }
    }

    private void summarize() {

        int size = messages.size();
        // 修复IndexOutOfBoundsException：确保batchSize不超过实际消息数量
        int actualBatchSize = Math.min(batchSize, size);
        List<Message> needSummaryMessages = new ArrayList<>(messages.subList(0, actualBatchSize));
        logger.info("current conversation message size:{}, batch size to summary:{}", size, actualBatchSize);
        // 调用大模型进行摘要。
        Thread.startVirtualThread(()->{
            // debug: 使用List副本，避免并发时异常
            summaryMessages(needSummaryMessages);
        });
    }

    protected void summaryMessages(List<Message> needSummaryMessages) {
        // 1. Process memory messages as a string.
        String memory = needSummaryMessages.stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(m -> m.getMessageType() + ":" + m.getText())
                .collect(Collectors.joining(System.lineSeparator()));

        // 2. 拼接提示词
        String factExtractPrompt = this.initSummarizerPromptTemplate.render(Map.of("datetime", LocalDate.now().toString(), "conversation", memory));

        // 3. Call the model.
        logger.info("调用大模型进行摘要：{}", factExtractPrompt);

        String factExtract = chatModel.call(factExtractPrompt);
        logger.info("大模型从对话里提取用户重要备忘: {}", factExtract);


        // 4. 入库存储。
        SysSummary lastSummary = new SysSummary()
                .setDeviceId(device().getDeviceId())
                .setRoleId(role().getRoleId())
                .setLastMessageTimestamp(Dialogue.getTimeMillis(needSummaryMessages.getLast()).truncatedTo(ChronoUnit.SECONDS))
                .setSummary(factExtract)
                .setCreateTime(Instant.now());
        chatMemory.save(lastSummary);
        // 5. 移除已处理的消息
        messages.removeAll(needSummaryMessages);
        this.lastSummary = lastSummary;
    }

    @Override
    public List<Message> messages() {

        // 新消息列表对象，避免使用过程中污染原始列表对象
        List<Message> historyMessages = new ArrayList<>();
        var roleSystemMessage = roleSystemMessage();
        if(roleSystemMessage.isPresent()){
            historyMessages.add(roleSystemMessage.get());
        }
        if(lastSummary!=null && StringUtils.hasText(lastSummary.getSummary())){
            // TODO 待实测求证多条SystemMessage是否会影响模型的指令遵循能力。Qwen系列模型可以多条SystemMessage。
            historyMessages.add(new SystemMessage("下面是你与用户最近聊天内容的摘要：\n"+lastSummary.getSummary()));
        }
        historyMessages.addAll(messages);
        return historyMessages;
    }

}
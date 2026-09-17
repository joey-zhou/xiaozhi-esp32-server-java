package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.common.port.ConfigLookup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 构造对话：加载历史与上次摘要，注入摘要器。设备与 Web 同一套，只是历史的归属不同。
 * <p>
 * RAG 召回已上移到 {@code Persona} 层（以 fake tool chain 形式注入 Conversation 历史并参与持久化）。
 */
@Service
@Slf4j
public class DefaultConversationFactory implements ConversationFactory {

    /** 条数兜底：正常由 token 预算触发，这里只防估算失灵 */
    @Value("${conversation.max-messages:60}")
    private int maxMessages;
    /** 压缩时保留最近的条数 */
    @Value("${conversation.keep-messages:8}")
    private int keepMessages;
    /** 上下文长度的多少作为 token 预算，其余留给模型输出与估算误差 */
    @Value("${conversation.context-budget-ratio:0.7}")
    private double contextBudgetRatio;
    /** 模型配置没填上下文长度时按这个算 */
    @Value("${conversation.default-context-length:32768}")
    private int defaultContextLength;
    /** 全程估算时给系统提示词与工具定义预留的 token */
    @Value("${conversation.prompt-overhead:4000}")
    private int promptOverhead;

    private final ChatMemory chatMemory;
    private final ChatModelFactory chatModelFactory;
    private final ConfigLookup configLookup;
    private final PromptTemplate initSummarizerTemplate = template("/prompts/init_summarizer.md");
    private final PromptTemplate againSummarizerTemplate = template("/prompts/again_summarizer.md");

    public DefaultConversationFactory(ChatMemory chatMemory, ChatModelFactory chatModelFactory, ConfigLookup configLookup) {
        this.chatMemory = chatMemory;
        this.chatModelFactory = chatModelFactory;
        this.configLookup = configLookup;
    }

    @Override
    public Conversation initConversation(String ownerId, Integer userId, RoleBO role, String sessionId) {
        return create(ownerId, userId, role, sessionId, false);
    }

    @Override
    public Conversation initSessionConversation(String ownerId, Integer userId, RoleBO role, String sessionId) {
        return create(ownerId, userId, role, sessionId, true);
    }

    private Conversation create(String ownerId, Integer userId, RoleBO role, String sessionId, boolean sessionScoped) {
        Integer roleId = role.getRoleId();
        SummaryBO last = sessionScoped
                ? chatMemory.findLastSummaryBySession(sessionId)
                : chatMemory.findLastSummary(ownerId, roleId);
        List<Message> history = history(ownerId, roleId, sessionId, sessionScoped, last);
        log.info("加载对话历史: ownerId={}, sessionId={}, sessionScoped={}, size={}, hasSummary={}",
                ownerId, sessionId, sessionScoped, history.size(), last != null);

        ChatModel chatModel = chatModelFactory.getChatModel(role);
        return Conversation.builder()
                .ownerId(ownerId)
                .roleId(roleId)
                .roleDesc(role.getRoleDesc())
                .userId(userId)
                .sessionId(sessionId)
                .history(history)
                .summary(last != null ? last.getSummary() : null)
                .summarizer(new LlmSummarizer(chatModel, initSummarizerTemplate, againSummarizerTemplate,
                        chatMemory, ownerId, roleId, sessionScoped ? sessionId : null))
                .maxMessages(maxMessages)
                .tokenBudget(tokenBudget(role))
                .promptOverhead(promptOverhead)
                .keepMessages(keepMessages)
                .build();
    }

    /** 角色所用模型配置里的上下文长度乘预算比例，没填按默认上下文长度算 */
    int tokenBudget(RoleBO role) {
        int contextLength = defaultContextLength;
        if (role.getModelId() != null) {
            ConfigBO config = configLookup.getConfig(role.getModelId());
            if (config != null && config.getContextLength() != null && config.getContextLength() > 0) {
                contextLength = config.getContextLength();
            }
        }
        return Math.max(1, (int) Math.round(contextLength * contextBudgetRatio));
    }

    /** 有摘要时只加载摘要之后的消息，没有时取最近 maxMessages 条 */
    private List<Message> history(String ownerId, Integer roleId, String sessionId, boolean sessionScoped,
                                  SummaryBO last) {
        if (last == null) {
            return sessionScoped
                    ? chatMemory.findBySession(sessionId, maxMessages)
                    : chatMemory.find(ownerId, roleId, maxMessages);
        }
        Instant since = last.getLastMessageTimestamp();
        return sessionScoped
                ? chatMemory.findBySession(sessionId, since)
                : chatMemory.find(ownerId, roleId, since);
    }

    private static PromptTemplate template(String path) {
        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('$').endDelimiterToken('$').build())
                .resource(new ClassPathResource(path, DefaultConversationFactory.class))
                .build();
    }
}

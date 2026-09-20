package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.utils.DateUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 用对话所用的模型合成摘要，并按对话归属写进摘要表。
 */
class LlmSummarizer implements Summarizer {

    private final ChatClient chatClient;
    private final PromptTemplate initTemplate;
    private final PromptTemplate againTemplate;
    private final ChatMemory chatMemory;
    private final String ownerId;
    private final Integer roleId;
    /** Web 会话的摘要按会话隔离，设备端为 null */
    private final String sessionId;

    LlmSummarizer(ChatModel chatModel, PromptTemplate initTemplate, PromptTemplate againTemplate,
                  ChatMemory chatMemory, String ownerId, Integer roleId, String sessionId) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
        this.initTemplate = initTemplate;
        this.againTemplate = againTemplate;
        this.chatMemory = chatMemory;
        this.ownerId = ownerId;
        this.roleId = roleId;
        this.sessionId = sessionId;
    }

    @Override
    public String summarize(String summary, List<Message> batch) {
        String conversation = MessageHistoryFormatter.format(batch);
        String prompt = StringUtils.hasText(summary)
                ? againTemplate.render(Map.of("last_summary", summary, "conversation", conversation))
                : initTemplate.render(Map.of("datetime", DateUtils.today().toString(), "conversation", conversation));
        String result = chatClient.prompt().user(prompt).call().content();
        if (!StringUtils.hasText(result)) {
            throw new IllegalStateException("摘要模型返回为空");
        }
        chatMemory.save(new SummaryBO()
                .setDeviceId(ownerId)
                .setRoleId(roleId)
                .setSessionId(sessionId)
                .setLastMessageTimestamp(MessageTimeMetadata.getTimeMillis(batch.getLast()))
                .setSummary(result)
                .setCreateTime(DateUtils.instant()));
        return result;
    }
}

package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.utils.AudioUtils;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 表示一次 Conversation 中已完成的一轮交互：
 * 一个 UserMessage，对应一个最终 AssistantMessage，以及这一轮产生的时序与工具调用信息。
 * <p>
 * 只是当前 Conversation 里的单轮结果对象。
 */
@Slf4j
@Value
public class DialogueTurn {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private UserMessage userMessage;
    private ChatResponse chatResponse;
    private Conversation conversation;
    private Instant userMessageCreatedAt;
    private Instant assistantMessageCreatedAt;
    private boolean disturbed;
    private List<DialogueContext.ToolCallInfo> toolCallDetails;
    private Path userSpeechPath;

    private final AssistantMessage assistantMessage;
    private final Duration timeToFirstToken;

    @Builder
    public DialogueTurn(
            UserMessage userMessage,
            ChatResponse chatResponse,
            Conversation conversation,
            Path userSpeechPath,
            Instant userMessageCreatedAt,
            Instant assistantMessageCreatedAt,
            boolean disturbed,
            List<DialogueContext.ToolCallInfo> toolCallDetails) {
        Assert.notNull(userMessage, "用户消息对象不应该为NULL！");
        Assert.notNull(chatResponse, "大语言模型的响应对象不应该为NULL！");
        Assert.notNull(conversation, "会话对象不应该为NULL！");
        Assert.notNull(userMessageCreatedAt, "用户消息创建时间对象不应该为NULL！");
        Assert.notNull(assistantMessageCreatedAt, "模型响应创建时间对象不应该为NULL！");
        this.userMessage = userMessage;
        this.chatResponse = chatResponse;
        this.conversation = conversation;
        this.userSpeechPath = userSpeechPath;
        this.userMessageCreatedAt = userMessageCreatedAt;
        this.assistantMessageCreatedAt = assistantMessageCreatedAt;
        this.disturbed = disturbed;
        this.toolCallDetails = toolCallDetails != null ? toolCallDetails : List.of();
        this.assistantMessage = chatResponse.getResult().getOutput();
        this.timeToFirstToken = Duration.between(userMessageCreatedAt, assistantMessageCreatedAt);
    }

    public List<MessageBO> toMessages() {
        Generation generation = chatResponse.getResult();
        Assert.notNull(generation, "Generation is null from ChatResponse");

        AssistantMessage assistantMessage = generation.getOutput();
        Usage llmUsage = chatResponse.getMetadata().getUsage();
        logTokenDetails(llmUsage);

        return List.of(userMessage, assistantMessage).stream()
            .map(message -> toMessageBO(message, llmUsage))
            .toList();
    }

    private MessageBO toMessageBO(org.springframework.ai.chat.messages.AbstractMessage message, Usage usage) {
        MessageBO messageBO = new MessageBO();
        messageBO.setUserId(conversation.device().getUserId());
        messageBO.setDeviceId(conversation.getDeviceId());
        messageBO.setSessionId(conversation.getSessionId());
        messageBO.setSender(message.getMessageType().getValue());
        messageBO.setMessage(message.getText());
        messageBO.setRoleId(conversation.getRoleId());
        messageBO.setMessageType(toolCallDetails.isEmpty()
            ? MessageBO.MESSAGE_TYPE_NORMAL
            : MessageBO.MESSAGE_TYPE_FUNCTION_CALL);

        switch (message.getMessageType()) {
            case USER:
                if (userSpeechPath != null) {
                    messageBO.setSttDuration(BigDecimal.valueOf(AudioUtils.getAudioDuration(userSpeechPath)));
                    messageBO.setAudioPath(userSpeechPath.toString());
                }
                messageBO.setCreateTime(LocalDateTime.ofInstant(userMessageCreatedAt, ZoneId.systemDefault()));
                messageBO.setTokens(usage == null ? 0 : usage.getPromptTokens());
                break;
            case ASSISTANT:
                messageBO.setTtfsTime(timeToFirstToken.toMillis());
                messageBO.setCreateTime(LocalDateTime.ofInstant(assistantMessageCreatedAt, ZoneId.systemDefault()));
                messageBO.setTokens(usage == null ? 0 : usage.getCompletionTokens());
                if (!toolCallDetails.isEmpty()) {
                    try {
                        messageBO.setToolCalls(OBJECT_MAPPER.writeValueAsString(toolCallDetails));
                    } catch (JsonProcessingException e) {
                        log.warn("序列化工具调用详情失败", e);
                    }
                }
                break;
            default:
                messageBO.setTokens(0);
        }

        messageBO.setResponseTime(0);
        return messageBO;
    }

    private void logTokenDetails(Usage usage) {
        if (!log.isDebugEnabled() || usage == null) {
            return;
        }
        if (usage instanceof DefaultUsage defaultUsage && defaultUsage.getNativeUsage() instanceof OpenAiApi.Usage openAiUsage) {
            var promptDetails = openAiUsage.promptTokensDetails();
            var completionDetails = openAiUsage.completionTokenDetails();
            log.debug("Token详情 — prompt: {} (cached: {}), completion: {} (reasoning: {}), total: {}",
                openAiUsage.promptTokens(),
                promptDetails != null ? promptDetails.cachedTokens() : "N/A",
                openAiUsage.completionTokens(),
                completionDetails != null ? completionDetails.reasoningTokens() : "N/A",
                openAiUsage.totalTokens());
        }
    }

    public void injectInstants() {
        MessageTimeMetadata.setTimeMillis(userMessage, userMessageCreatedAt);
        MessageTimeMetadata.setTimeMillis(assistantMessage, assistantMessageCreatedAt);
    }
}

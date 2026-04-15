package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.ai.llm.memory.Conversation;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.Assert;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

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
    private List<DialogueContext.ToolCallInfo> toolCallDetails;
    private Path userSpeechPath;
    private AssistantMessage toolCallAssistantMessage;
    private ToolResponseMessage toolResponseMessage;

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
            List<DialogueContext.ToolCallInfo> toolCallDetails,
            AssistantMessage toolCallAssistantMessage,
            ToolResponseMessage toolResponseMessage) {
        Assert.notNull(userMessage, "用户消息对象不应该为NULL！");
        Assert.notNull(chatResponse, "大语言模型的响应对象不应该为NULL！");
        Assert.notNull(conversation, "会话对象不应该为NULL！");
        Assert.notNull(userMessageCreatedAt, "用户消息创建时间对象不应该为NULL！");
        Assert.notNull(assistantMessageCreatedAt, "模型响应创建时间对象不应该为NULL！");
        this.userMessage = userMessage;
        this.chatResponse = chatResponse;
        this.conversation = conversation;
        this.userSpeechPath = userSpeechPath;
        this.timeToFirstToken = Duration.between(userMessageCreatedAt, assistantMessageCreatedAt);
        this.userMessageCreatedAt = userMessageCreatedAt.truncatedTo(ChronoUnit.SECONDS);
        this.assistantMessageCreatedAt = assistantMessageCreatedAt.truncatedTo(ChronoUnit.SECONDS);
        this.toolCallDetails = toolCallDetails != null ? toolCallDetails : List.of();
        this.toolCallAssistantMessage = toolCallAssistantMessage;
        this.toolResponseMessage = toolResponseMessage;
        this.assistantMessage = chatResponse.getResult().getOutput();
    }

    public List<MessageBO> toMessages() {
        Generation generation = chatResponse.getResult();
        Assert.notNull(generation, "Generation is null from ChatResponse");

        AssistantMessage finalAssistantMessage = generation.getOutput();
        Usage llmUsage = chatResponse.getMetadata().getUsage();

        List<MessageBO> messages = new ArrayList<>();

        // 1. UserMessage
        messages.add(toMessageBO(userMessage, llmUsage));

        // 2+3. 如果有工具调用，插入中间消息：Assistant(toolCall) + Tool(response)
        if (toolCallAssistantMessage != null && toolResponseMessage != null) {
            messages.add(toToolCallAssistantMessageBO());
            messages.add(toToolResponseMessageBO());
        }

        // 4. 最终 AssistantMessage
        messages.add(toMessageBO(finalAssistantMessage, llmUsage));

        return messages;
    }

    private MessageBO toMessageBO(org.springframework.ai.chat.messages.AbstractMessage message, Usage usage) {
        MessageBO messageBO = new MessageBO();
        messageBO.setUserId(conversation.device().getUserId());
        messageBO.setDeviceId(conversation.getDeviceId());
        messageBO.setSessionId(conversation.getSessionId());
        messageBO.setSender(message.getMessageType().getValue());
        messageBO.setMessage(message.getText());
        messageBO.setRoleId(conversation.getRoleId());
        messageBO.setMessageType(MessageBO.MESSAGE_TYPE_NORMAL);

        switch (message.getMessageType()) {
            case USER:
                if (userSpeechPath != null) {
                    messageBO.setAudioPath(userSpeechPath.toString());
                }
                messageBO.setCreateTime(LocalDateTime.ofInstant(userMessageCreatedAt, ZoneId.systemDefault()));
                break;
            case ASSISTANT:
                messageBO.setCreateTime(LocalDateTime.ofInstant(assistantMessageCreatedAt, ZoneId.systemDefault()));
                if (!toolCallDetails.isEmpty()) {
                    try {
                        messageBO.setToolCalls(OBJECT_MAPPER.writeValueAsString(toolCallDetails));
                    } catch (JsonProcessingException e) {
                        log.warn("序列化工具调用详情失败", e);
                    }
                }
                break;
            default:
                break;
        }

        return messageBO;
    }

    /**
     * 构建工具调用请求的 MessageBO（sender=assistant, messageType=TOOL_CALL）
     */
    private MessageBO toToolCallAssistantMessageBO() {
        MessageBO messageBO = new MessageBO();
        messageBO.setUserId(conversation.device().getUserId());
        messageBO.setDeviceId(conversation.getDeviceId());
        messageBO.setSessionId(conversation.getSessionId());
        messageBO.setSender(Conversation.MESSAGE_TYPE_ASSISTANT);
        messageBO.setMessage(toolCallAssistantMessage.getText());
        messageBO.setRoleId(conversation.getRoleId());
        messageBO.setMessageType(MessageBO.MESSAGE_TYPE_TOOL_CALL);
        messageBO.setCreateTime(LocalDateTime.ofInstant(assistantMessageCreatedAt, ZoneId.systemDefault()));
        // 存储 toolCalls JSON: [{id, name, arguments}]
        try {
            var toolCallsJson = toolCallAssistantMessage.getToolCalls().stream()
                    .map(tc -> Map.of("id", tc.id(), "name", tc.name(), "arguments", tc.arguments()))
                    .toList();
            messageBO.setToolCalls(OBJECT_MAPPER.writeValueAsString(toolCallsJson));
        } catch (JsonProcessingException e) {
            log.warn("序列化 tool call 请求失败", e);
        }
        return messageBO;
    }

    /**
     * 构建工具执行结果的 MessageBO（sender=tool, messageType=TOOL_RESPONSE）
     */
    private MessageBO toToolResponseMessageBO() {
        MessageBO messageBO = new MessageBO();
        messageBO.setUserId(conversation.device().getUserId());
        messageBO.setDeviceId(conversation.getDeviceId());
        messageBO.setSessionId(conversation.getSessionId());
        messageBO.setSender(Conversation.MESSAGE_TYPE_TOOL);
        // 合并所有 ToolResponse 的文本
        String responseText = toolResponseMessage.getResponses().stream()
                .map(ToolResponseMessage.ToolResponse::responseData)
                .collect(Collectors.joining("\n"));
        messageBO.setMessage(responseText);
        messageBO.setRoleId(conversation.getRoleId());
        messageBO.setMessageType(MessageBO.MESSAGE_TYPE_TOOL_RESPONSE);
        messageBO.setCreateTime(LocalDateTime.ofInstant(assistantMessageCreatedAt, ZoneId.systemDefault()));
        // 存储 toolCallId 和 toolName 信息
        try {
            var responseInfo = toolResponseMessage.getResponses().stream()
                    .map(r -> Map.of("toolCallId", r.id(), "toolName", r.name()))
                    .toList();
            messageBO.setToolCalls(OBJECT_MAPPER.writeValueAsString(responseInfo));
        } catch (JsonProcessingException e) {
            log.warn("序列化 tool response 信息失败", e);
        }
        return messageBO;
    }

    public void injectInstants() {
        MessageTimeMetadata.setTimeMillis(userMessage, userMessageCreatedAt);
        MessageTimeMetadata.setTimeMillis(assistantMessage, assistantMessageCreatedAt);
    }
}

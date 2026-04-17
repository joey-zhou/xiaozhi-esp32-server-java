package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.summary.service.SummaryService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于数据库的聊天记忆实现。
 * 全局单例类，负责 Conversation 里消息的获取、保存、清理。
 */
@Service
public class DatabaseChatMemory implements ChatMemory {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseChatMemory.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SummaryService summaryService;
    private final MessageService messageService;

    @Autowired
    public DatabaseChatMemory(MessageService messageService, SummaryService summaryService) {
        this.messageService = messageService;
        this.summaryService = summaryService;
    }

    @Override
    public void save(SummaryBO summary) {
        summaryService.save(summary);
    }

    @Override
    public SummaryBO findLastSummary(String ownerId, int roleId) {
        return summaryService.findLast(ownerId, roleId);
    }

    @Override
    public List<Message> find(String ownerId, int roleId, int limit) {
        try {
            return toSpringMessages(messageService.listHistory(ownerId, roleId, limit));
        } catch (Exception e) {
            logger.error("获取历史消息时出错(按 ownerId+roleId): {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Message> find(String sessionId, int limit) {
        try {
            return toSpringMessages(messageService.listHistory(sessionId, limit));
        } catch (Exception e) {
            logger.error("获取历史消息时出错(按 sessionId): {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public static @NotNull Message toSpringMessage(MessageBO message) {
        String role = message.getSender();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("messageId", message.getMessageId());

        Message springMessage;
        if (MessageBO.SENDER_TOOL.equals(role)) {
            // ToolResponseMessage: 从 toolCalls 字段恢复 toolCallId 和 toolName
            springMessage = buildToolResponseMessage(message);
        } else if (MessageBO.SENDER_ASSISTANT.equals(role)) {
            // TOOL_CALL 类型的 AssistantMessage 需要恢复 toolCalls
            if (MessageBO.MESSAGE_TYPE_TOOL_CALL.equals(message.getMessageType())) {
                springMessage = buildToolCallAssistantMessage(message, metadata);
            } else {
                springMessage = AssistantMessage.builder().content(message.getMessage()).properties(metadata).build();
            }
        } else if (MessageBO.SENDER_USER.equals(role)) {
            springMessage = UserMessage.builder().text(message.getMessage()).metadata(metadata).build();
        } else {
            throw new IllegalArgumentException("Invalid role: " + role);
        }

        if (message.getCreateTime() != null) {
            MessageTimeMetadata.setTimeMillis(
                springMessage,
                message.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()
            );
        }
        return springMessage;
    }

    /**
     * 从 DB 记录重建带 toolCalls 的 AssistantMessage
     */
    private static AssistantMessage buildToolCallAssistantMessage(MessageBO message, Map<String, Object> metadata) {
        List<AssistantMessage.ToolCall> toolCalls = List.of();
        if (message.getToolCalls() != null) {
            try {
                List<Map<String, String>> rawList = OBJECT_MAPPER.readValue(
                        message.getToolCalls(), new TypeReference<>() {});
                toolCalls = rawList.stream()
                        .map(m -> new AssistantMessage.ToolCall(
                                m.getOrDefault("id", ""),
                                "function",
                                m.getOrDefault("name", ""),
                                m.getOrDefault("arguments", "")))
                        .toList();
            } catch (Exception e) {
                logger.warn("反序列化 toolCalls 失败: {}", e.getMessage());
            }
        }
        return AssistantMessage.builder()
                .content(message.getMessage())
                .properties(metadata)
                .toolCalls(toolCalls)
                .build();
    }

    /**
     * 从 DB 记录重建 ToolResponseMessage
     */
    private static ToolResponseMessage buildToolResponseMessage(MessageBO message) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        if (message.getToolCalls() != null) {
            try {
                List<Map<String, String>> rawList = OBJECT_MAPPER.readValue(
                        message.getToolCalls(), new TypeReference<>() {});
                for (Map<String, String> m : rawList) {
                    responses.add(new ToolResponseMessage.ToolResponse(
                            m.getOrDefault("toolCallId", ""),
                            m.getOrDefault("toolName", ""),
                            message.getMessage()));
                }
            } catch (Exception e) {
                logger.warn("反序列化 tool response 信息失败: {}", e.getMessage());
            }
        }
        if (responses.isEmpty()) {
            responses.add(new ToolResponseMessage.ToolResponse("", "", message.getMessage()));
        }
        return ToolResponseMessage.builder().responses(responses).build();
    }

    public static List<Message> toSpringMessages(List<MessageBO> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        return messages.stream()
            .filter(message -> MessageBO.SENDER_ASSISTANT.equals(message.getSender())
                || MessageBO.SENDER_USER.equals(message.getSender())
                || MessageBO.SENDER_TOOL.equals(message.getSender()))
            .map(DatabaseChatMemory::toSpringMessage)
            .collect(Collectors.toList());
    }

    @Override
    public List<Message> find(String ownerId, int roleId, Instant timeMillis) {
        return toSpringMessages(messageService.listHistoryAfter(ownerId, roleId, timeMillis));
    }

    @Override
    public void delete(String ownerId, int roleId) {
        try {
            throw new IllegalAccessException("暂不支持删除历史记录");
        } catch (Exception e) {
            logger.error("清除历史记录时出错: {}", e.getMessage(), e);
        }
    }
}

package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.summary.service.SummaryService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    public SummaryBO findLastSummary(String deviceId, int roleId) {
        return summaryService.findLast(deviceId, roleId);
    }

    @Override
    public List<Message> find(String deviceId, int roleId, int limit) {
        try {
            List<MessageBO> messages = new ArrayList<>(messageService.listHistory(deviceId, roleId, limit));
            messages.sort(Comparator.comparing(MessageBO::getCreateTime, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(MessageBO::getSender, Comparator.reverseOrder()));
            return toSpringMessages(messages);
        } catch (Exception e) {
            logger.error("获取历史消息时出错: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public static @NotNull Message toSpringMessage(MessageBO message) {
        String role = message.getSender();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("messageId", message.getMessageId());
        metadata.put(ChatMemory.MESSAGE_TYPE_KEY, message.getMessageType());

        Message springMessage;
        if (MessageType.ASSISTANT.getValue().equals(role)) {
            springMessage = AssistantMessage.builder().content(message.getMessage()).properties(metadata).build();
        } else if (MessageType.USER.getValue().equals(role)) {
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

    public static List<Message> toSpringMessages(List<MessageBO> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        return messages.stream()
            .filter(message -> MessageType.ASSISTANT.getValue().equals(message.getSender())
                || MessageType.USER.getValue().equals(message.getSender()))
            .map(DatabaseChatMemory::toSpringMessage)
            .collect(Collectors.toList());
    }

    @Override
    public List<Message> find(String deviceId, int roleId, Instant timeMillis) {
        return toSpringMessages(messageService.listHistoryAfter(deviceId, roleId, timeMillis));
    }

    @Override
    public void delete(String deviceId, int roleId) {
        try {
            throw new IllegalAccessException("暂不支持删除设备历史记录");
        } catch (Exception e) {
            logger.error("清除设备历史记录时出错: {}", e.getMessage(), e);
        }
    }
}

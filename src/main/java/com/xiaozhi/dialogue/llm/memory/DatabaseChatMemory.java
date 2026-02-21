package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.dao.MessageMapper;
import com.xiaozhi.dao.SummaryMapper;
import com.xiaozhi.entity.SysSummary;
import com.xiaozhi.entity.SysMessage;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于数据库的聊天记忆实现
 * 全局单例类，负责Conversation里消息的获取、保存、清理。
 * 后续考虑：DatabaseChatMemory 是对 SysMessageService 的一层薄封装，未来或者有可能考虑合并这两者。
 */
@Service
public class DatabaseChatMemory  implements ChatMemory {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseChatMemory.class);


    private final SummaryMapper summaryMapper;
    private final MessageMapper messageMapper;

    @Autowired
    public DatabaseChatMemory(MessageMapper messageMapper,SummaryMapper summaryMapper) {
        this.messageMapper = messageMapper;
        this.summaryMapper = summaryMapper;
    }

    @Override
    public void save(SysSummary summary) {
        summaryMapper.saveSummary(summary);
    }

    @Override
    public SysSummary findLastSummary(String deviceId, int roleId) {
        SysSummary summary = summaryMapper.findLastSummary(deviceId, roleId);
        return summary;
    }

    @Override
    public List<Message> find(String deviceId, int roleId, int limit) {
        try {
            List<SysMessage> messages = messageMapper.find(deviceId, roleId, limit);
            messages = new ArrayList<>(messages);
            // 按时间升序排序，时间相同时按sender降序排序，保持user在assistant前面
            messages.sort(Comparator.<SysMessage, Date>comparing(SysMessage::getCreateTime)
                                    .thenComparing(SysMessage::getSender, Comparator.reverseOrder()));
            if (messages == null || messages.isEmpty()) {
                return Collections.emptyList();
            }
            return messages.stream()
                    .filter(message -> MessageType.ASSISTANT.getValue().equals(message.getSender())
                            || MessageType.USER.getValue().equals(message.getSender()))
                    .map(DatabaseChatMemory::convert).collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("获取历史消息时出错: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public static @NotNull AbstractMessage convert(SysMessage message) {
        String role = message.getSender();
        // 一般消息("messageType", "NORMAL");//默认为普通消息
        Map<String, Object> metadata = Map.of("messageId", message.getMessageId(), ChatMemory.MESSAGE_TYPE_KEY,
                message.getMessageType());

        if (MessageType.ASSISTANT.getValue().equals(role)) {
            return AssistantMessage.builder().content(message.getMessage()).properties(metadata).build();
        }
        if (MessageType.USER.getValue().equals(role)) {
            return UserMessage.builder().text(message.getMessage()).metadata(metadata).build();
        }
        throw new IllegalArgumentException("Invalid role: " + role);
    }

    /**
     * 将数据库记录的SysMessag转换为spring-ai的Message。
     *
     * @param messages
     * @return
     */
    public static List<AbstractMessage> convert(List<SysMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        return messages.stream()
                .filter(message -> MessageType.ASSISTANT.getValue().equals(message.getSender())
                        || MessageType.USER.getValue().equals(message.getSender()))
                .map(DatabaseChatMemory::convert)
                .collect(Collectors.toList());
    }


    @Override
    public List<Message> find(String deviceId, int roleId, Instant timeMillis){
        List<SysMessage> messages = messageMapper.findAfter(deviceId, roleId, timeMillis);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        return messages.stream()
                .filter(message -> MessageType.ASSISTANT.getValue().equals(message.getSender())
                        || MessageType.USER.getValue().equals(message.getSender()))
                .map(DatabaseChatMemory::convert).collect(Collectors.toList());
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
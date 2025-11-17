package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.dao.MessageMapper;
import com.xiaozhi.entity.Base;
import com.xiaozhi.entity.SysMessage;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于数据库的聊天记忆实现
 * 全局单例类，负责Conversatin里消息的获取、保存、清理。
 * 后续考虑：DatabaseChatMemory 是对 SysMessageService 的一层薄封装，未来或者有可能考虑合并这两者。
 */
@Service
public class DatabaseChatMemory  implements ChatMemory {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseChatMemory.class);


    private final MessageMapper messageMapper;

    @Autowired
    public DatabaseChatMemory(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public void save(String deviceId, Integer roleId, String sessionId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return ;
        }
        // 异步虚拟线程处理持久化。
        Thread.startVirtualThread(() -> {
            try {
                // 创建消息列表的副本，避免并发修改异常
                // 使用synchronized确保线程安全
                List<Message> messagesCopy;
                synchronized (messages) {
                    messagesCopy = new ArrayList<>(messages);
                }
                
                List<SysMessage> messageList = messagesCopy.stream()
                        .filter(msg -> msg != null) // 过滤掉null消息
                        .map(msg -> {
                            try {
                                SysMessage message = new SysMessage();
                                message.setDeviceId(deviceId);
                                message.setSessionId(sessionId);
                                message.setSender(msg.getMessageType().getValue());
                                message.setMessage(msg.getText());
                                message.setRoleId(roleId);
                                String sysMessageType = ChatMemory.getSysMessageType(msg);
                                message.setMessageType(sysMessageType);
                                Long timeMillis = ChatMemory.getTimeMillis(msg);
                                Instant instant = Instant.ofEpochMilli(timeMillis).truncatedTo(ChronoUnit.SECONDS);
                                message.setCreateTime(Date.from(instant));

                                return message;
                            } catch (Exception msgException) {
                                logger.warn("处理单个消息时出错，跳过该消息: {}", msgException.getMessage());
                                return null;
                            }
                        })
                        .filter(msg -> msg != null) // 过滤掉处理失败的消息
                        .toList();
                        
                if (!messageList.isEmpty()) {
                    messageMapper.saveAll(messageList);
                }
            } catch (Exception e) {
                logger.error("保存消息时出错: {}", e.getMessage(), e);
            }
        });
    }

    @Override
    public List<Message> find(String deviceId, int roleId, int limit) {
        try {
            List<SysMessage> messages = messageMapper.find(deviceId, roleId, limit);
            messages = new ArrayList<>(messages);
            messages.sort(Comparator.comparing(Base::getCreateTime));
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

    private static @NotNull AbstractMessage convert(SysMessage message) {
        String role = message.getSender();
        // 一般消息("messageType", "NORMAL");//默认为普通消息
        Map<String, Object> metadata = Map.of("messageId", message.getMessageId(), ChatMemory.MESSAGE_TYPE_KEY,
                message.getMessageType());
        return switch (role) {
            case "assistant" -> new AssistantMessage(message.getMessage(), metadata);
            case "user" -> UserMessage.builder().text(message.getMessage()).metadata(metadata).build();
            default -> throw new IllegalArgumentException("Invalid role: " + role);
        };
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
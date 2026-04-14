package com.xiaozhi.ai.llm.memory;

import org.springframework.ai.chat.messages.Message;

import java.time.Instant;

/**
 * Conversation 运行时消息元数据工具。
 * 目前主要负责在 Spring AI Message 上读写对话时间戳，
 * 供 DialogueTurn、SummaryConversation 等运行时/记忆组件复用。
 */
public final class MessageTimeMetadata {

    private MessageTimeMetadata() {
    }

    public static void setTimeMillis(Message message, Instant timeMillis) {
        message.getMetadata().put(ChatMemory.TIME_MILLIS_KEY, timeMillis);
    }

    public static Instant getTimeMillis(Message message) {
        return (Instant) message.getMetadata().getOrDefault(ChatMemory.TIME_MILLIS_KEY, Instant.now());
    }
}

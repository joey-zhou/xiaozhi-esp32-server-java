package com.xiaozhi.communication.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.event.TtsPlaybackCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class MessageSender {

    private static final Logger logger = LoggerFactory.getLogger(MessageSender.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ApplicationEventPublisher eventPublisher;

    public MessageSender(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void sendTtsMessage(ChatSession session, String text, String state) {
        if (session == null || !session.isOpen()) {
            logger.error("ChatSession为null 或者已关闭，请检查！{}", Arrays.toString(Thread.currentThread().getStackTrace()));
            return;
        }
        ObjectNode messageJson = objectMapper.createObjectNode();
        messageJson.put("type", "tts");
        messageJson.put("state", state);
        if (text != null) {
            messageJson.put("text", text);
        }

        String jsonMessage = messageJson.toString();
        logger.info("sendTtsMessage发送消息 - SessionId: {}, Message: {}", session.getSessionId(), jsonMessage);
        sendTextMessage(session, jsonMessage);

        if ("stop".equals(state)) {
            eventPublisher.publishEvent(new TtsPlaybackCompletedEvent(this, session.getSessionId()));
        }
    }

    public void sendSttMessage(ChatSession session, String text) {
        if (session == null || !session.isOpen()) {
            logger.warn("sendSttMessage无法发送消息 - 会话已关闭或为null");
            return;
        }
        ObjectNode messageJson = objectMapper.createObjectNode();
        messageJson.put("type", "stt");
        messageJson.put("text", text);

        String jsonMessage = messageJson.toString();
        logger.info("sendSttMessage发送消息 - SessionId: {}, Message: {}", session.getSessionId(), jsonMessage);
        sendTextMessage(session, jsonMessage);
    }

    public void sendIotCommandMessage(ChatSession session, List<Map<String, Object>> commands) {
        if (session == null || !session.isOpen()) {
            logger.warn("sendIotCommandMessage无法发送消息 - 会话已关闭或为null");
            return;
        }
        ObjectNode messageJson = objectMapper.createObjectNode();
        messageJson.put("session_id", session.getSessionId());
        messageJson.put("type", "iot");
        messageJson.set("commands", objectMapper.valueToTree(commands));

        String jsonMessage = messageJson.toString();
        logger.debug("sendIotCommandMessage发送iot消息 - SessionId: {}, Message: {}", session.getSessionId(), messageJson);
        sendTextMessage(session, jsonMessage);
    }

    public void sendEmotion(ChatSession session, String emotion) {
        if (session == null || !session.isOpen()) {
            logger.warn("sendEmotion无法发送消息 - 会话已关闭或为null");
            return;
        }
        ObjectNode messageJson = objectMapper.createObjectNode();
        messageJson.put("session_id", session.getSessionId());
        messageJson.put("type", "llm");
        messageJson.put("emotion", emotion);
        messageJson.put("text", emotion);
        String jsonMessage = messageJson.toString();
        logger.info("sendEmotion发送Emotion消息 - SessionId: {}, Message: {}", session.getSessionId(), jsonMessage);
        sendTextMessage(session, jsonMessage);
    }

    public void sendTextMessage(ChatSession chatSession, String message) {
        try {
            if (chatSession == null || !chatSession.isOpen()) {
                return;
            }
            chatSession.sendTextMessage(message);
        } catch (Exception e) {
            logger.error("发送消息时发生异常 - SessionId: {}, Error: {}", chatSession.getSessionId(), e.getMessage());
            throw new RuntimeException("发送消息失败, 消息内容: " + message, e);
        }
    }

    public void sendBinaryMessage(ChatSession chatSession, byte[] opusFrame) {
        try {
            if (chatSession == null || !chatSession.isOpen()) {
                return;
            }
            chatSession.sendBinaryMessage(opusFrame);
        } catch (Exception e) {
            logger.error("发送消息时发生异常 - SessionId: {}, Error: {}", chatSession.getSessionId(), e.getMessage());
            throw new RuntimeException("发送音频消息失败, 消息内容", e);
        }
    }
}

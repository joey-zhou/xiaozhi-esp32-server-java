package com.xiaozhi.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Service("WebSocketMessageService")
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发送文本消息给指定会话 - 响应式版本
     * 
     * @param session WebSocket会话
     * @param type    消息类型
     * @param state   消息状态
     * @return Mono<Void> 操作结果
     */
    public Mono<Void> sendMessage(WebSocketSession session, String type, String state) {
        if (session == null || !session.isOpen()) {
            logger.warn("无法发送消息 - 会话已关闭或为null");
            return Mono.empty();
        }

        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("session_id", session.getId());
            response.put("type", type);
            response.put("state", state);

            String jsonMessage = response.toString();
            logger.info("发送消息 - SessionId: {}, Message: {}", session.getId(), jsonMessage);

            return session.send(Mono.just(session.textMessage(jsonMessage)));
        } catch (Exception e) {
            logger.error("发送消息时发生异常 - SessionId: {}, Error: {}", session.getId(), e.getMessage());
            return Mono.error(e);
        }
    }

    /**
     * 发送带文本内容的消息给指定会话 - 响应式版本
     * 
     * @param session WebSocket会话
     * @param type    消息类型
     * @param state   消息状态
     * @param message 消息文本内容
     * @return Mono<Void> 操作结果
     */
    public Mono<Void> sendMessage(WebSocketSession session, String type, String state, String message) {
        if (session == null || !session.isOpen()) {
            logger.warn("无法发送消息 - 会话已关闭或为null");
            return Mono.empty();
        }

        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("session_id", session.getId());
            response.put("type", type);
            response.put("state", state);
            response.put("text", message);

            String jsonMessage = response.toString();
            logger.info("发送消息 - SessionId: {}, Message: {}", session.getId(), jsonMessage);

            return session.send(Mono.just(session.textMessage(jsonMessage)));
        } catch (Exception e) {
            logger.error("发送消息时发生异常 - SessionId: {}, Error: {}", session.getId(), e.getMessage());
            return Mono.error(e);
        }
    }

    public Mono<Void> sendMessage(WebSocketSession session, String type) {
        if (session == null || !session.isOpen()) {
            logger.warn("无法发送消息 - 会话已关闭或为null");
            return Mono.empty();
        }

        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("session_id", session.getId());
            response.put("type", type);

            String jsonMessage = response.toString();
            logger.info("发送消息 - SessionId: {}, Message: {}", session.getId(), jsonMessage);

            return session.send(Mono.just(session.textMessage(jsonMessage)));
        } catch (Exception e) {
            logger.error("发送消息时发生异常 - SessionId: {}, Error: {}", session.getId(), e.getMessage());
            return Mono.error(e);
        }
    }

}
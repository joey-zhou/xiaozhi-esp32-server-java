package com.xiaozhi.communication.server.websocket;

import com.xiaozhi.communication.common.ChatSession;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.SessionLimitExceededException;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketSession extends ChatSession {
    /**
     * 当前会话的链接 session
     */
    protected org.springframework.web.socket.WebSocketSession session;

    public WebSocketSession(String sessionId) {
        super(sessionId);
    }

    public WebSocketSession(org.springframework.web.socket.WebSocketSession session) {
        super(session.getId());
        this.session = session;
    }

    @Override
    public String getSessionId() {
        return session.getId();
    }

    public org.springframework.web.socket.WebSocketSession getSession() {
        return this.session;
    }

    @Override
    public void close() {
        if(session != null){
            try {
                session.close();
            } catch (IOException e) {
                log.error("关闭WebSocket会话时发生错误 - SessionId: {}", getSessionId(), e);
            }
        }
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public boolean isAudioChannelOpen() {
        return session.isOpen();
    }

    /**
     * 这里的 session 必须是串行化装饰过的实例，容器不允许并发写同一个端点。
     * 并发写与缓冲超限抛的是运行时异常，必须一并捕获，漏出去会打死调用方的播放线程。
     */
    @Override
    public void sendTextMessage(String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (SessionLimitExceededException e) {
            handleSendLimitExceeded(e);
        } catch (Exception e) {
            handleSendFailure("Text", e);
        }
    }

    @Override
    public void sendBinaryMessage(byte[] message, long timestamp) {
        try {
            session.sendMessage(new BinaryMessage(
                    BinaryProtocolCodec.encode(protocolVersion, message, timestamp)));
        } catch (SessionLimitExceededException e) {
            handleSendLimitExceeded(e);
        } catch (Exception e) {
            handleSendFailure("Binary", e);
        }
    }

    /**
     * 发送超时说明对端已经收不下数据，装饰器此后会静默丢掉全部下行，
     * 不主动断开就会留下一条「连着但永远不出声」的僵尸会话。
     */
    private void handleSendLimitExceeded(SessionLimitExceededException e) {
        log.warn("下行发送超出限制，关闭会话 - SessionId: {}, 原因: {}", getSessionId(), e.getMessage());
        close();
    }

    /**
     * 其余发送异常（连接已被对端断开等）同样代表这条连接不能再用，
     * 只打日志不关闭的话，播放循环感知不到断连，会对着一个死连接持续发送。
     */
    private void handleSendFailure(String messageType, Exception e) {
        log.error("发送{}消息失败，关闭会话 - SessionId: {}", messageType, getSessionId(), e);
        close();
    }
}

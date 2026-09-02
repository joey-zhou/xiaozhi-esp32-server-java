package com.xiaozhi.communication.server.websocket;

import com.xiaozhi.communication.common.*;
import com.xiaozhi.communication.domain.*;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WebSocketHandler extends AbstractWebSocketHandler {
    @Resource
    private SessionManager sessionManager;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private DeviceMcpService deviceMcpService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, String> headers = getHeadersFromSession(session);
        String deviceIdAuth = headers.get("device-id");
        String token = headers.get("Authorization");
        if (deviceIdAuth == null || deviceIdAuth.isEmpty()) {
            log.error("设备ID为空");
            try {
                session.close(CloseStatus.BAD_DATA.withReason("设备ID为空"));
            } catch (IOException e) {
                log.error("关闭WebSocket连接失败", e);
            }
            return;
        }

        com.xiaozhi.communication.server.websocket.WebSocketSession xiaoZhiSession
                = new com.xiaozhi.communication.server.websocket.WebSocketSession(session);
        // 握手头先给出版本，hello 到达后以其声明为准
        xiaoZhiSession.setProtocolVersion(resolveProtocolVersion(
                parseVersion(session.getHandshakeHeaders().getFirst("Protocol-Version")), session.getId()));
        messageHandler.afterConnection(xiaoZhiSession, deviceIdAuth);
        sessionManager.openAudioChannel(xiaoZhiSession.getSessionId(), deviceIdAuth);

        log.info("WebSocket连接建立成功 - SessionId: {}, DeviceId: {}", session.getId(), deviceIdAuth);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        ChatSession chatSession = sessionManager.getSession(sessionId);
        DeviceBO device = chatSession != null ? chatSession.getDevice() : null;
        String payload = message.getPayload();

        try {
            var msg = JsonUtil.fromJson(payload, Message.class);
            if (Objects.requireNonNull(msg) instanceof HelloMessage m) {
                handleHelloMessage(session, m);
            } else {
                if (device == null || device.getRoleId() == null) {
                    // 设备未绑定，尝试自动绑定
                    boolean autoBound = messageHandler.handleUnboundDevice(sessionId, device);
                    if (!autoBound) {
                        // 自动绑定失败或需要验证码，不继续处理消息
                        return;
                    }
                    // 自动绑定成功，重新获取设备信息
                    device = chatSession != null ? chatSession.getDevice() : null;
                    if (device == null || device.getRoleId() == null) {
                        log.warn("自动绑定后设备信息异常 - SessionId: {}", sessionId);
                        return;
                    }
                    log.info("自动绑定成功，继续处理消息 - SessionId: {}, DeviceId: {}", sessionId, device.getDeviceId());
                }
                messageHandler.handleMessage(msg, sessionId);
            }
        } catch (Exception e) {
            log.error("handleTextMessage处理失败", e);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String sessionId = session.getId();
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if (chatSession == null || chatSession.getDevice() == null) {
            return;
        }
        ByteBuffer buffer = message.getPayload();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        int version = chatSession.getProtocolVersion();
        BinaryProtocolCodec.Frame frame = BinaryProtocolCodec.decode(version, data);
        if (frame == null) {
            // 设备声明的版本与实际帧格式不符，整个会话降回 v1 裸帧（收发同源，下行一并降级）
            log.warn("二进制帧与协议v{}不符，会话降级为v1 - SessionId: {}, 帧长: {}", version, sessionId, data.length);
            chatSession.setProtocolVersion(BinaryProtocolCodec.VERSION_V1);
            frame = new BinaryProtocolCodec.Frame(data, 0);
        }
        messageHandler.handleBinaryMessage(sessionId, frame.payload(), frame.timestamp());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        messageHandler.afterConnectionClosed(sessionId);

        log.info("WebSocket连接关闭 - SessionId: {}, 状态: {}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = session.getId();
        // 检查是否是客户端正常关闭连接导致的异常
        if (isClientCloseRequest(exception)) {
            // 客户端主动关闭，记录为信息级别日志而非错误
            log.info("WebSocket连接被客户端主动关闭 - SessionId: {}", sessionId);
            messageHandler.afterConnectionClosed(sessionId);
        } else {
            // 真正的传输错误
            log.error("WebSocket传输错误 - SessionId: {}", sessionId, exception);
        }
    }

    /**
     * 判断异常是否由客户端主动关闭连接导致
     */
    private boolean isClientCloseRequest(Throwable exception) {
        // 检查常见的客户端关闭连接导致的异常类型
        if (exception instanceof IOException) {
            String message = exception.getMessage();
            if (message != null) {
                return message.contains("Connection reset by peer") ||
                    message.contains("Broken pipe") ||
                    message.contains("Connection closed") ||
                    message.contains("远程主机强迫关闭了一个现有的连接");
            }
            // 处理EOFException，这通常是客户端关闭连接导致的
            return exception instanceof java.io.EOFException;
        }
        return false;
    }

    private void handleHelloMessage(WebSocketSession session, HelloMessage message) {
        var sessionId = session.getId();
        log.info("收到hello消息 - SessionId: {}, JsonNode: {}", sessionId, message);

        messageHandler.applyAecCapability(sessionId, message);

        ChatSession current = sessionManager.getSession(sessionId);
        int protocolVersion = current != null ? current.getProtocolVersion() : BinaryProtocolCodec.VERSION_V1;
        // hello 未声明版本时沿用握手头协商的结果
        if (message.getVersion() != null) {
            protocolVersion = resolveProtocolVersion(message.getVersion(), sessionId);
            if (current != null) {
                current.setProtocolVersion(protocolVersion);
            }
        }

        messageHandler.applyAudioParams(sessionId, message.getAudioParams());

        // 回复hello消息
        var resp = new HelloMessageResp()
                .setVersion(protocolVersion)
                .setTransport("websocket")
                .setSessionId(sessionId)
                .setAudioParams(AudioParams.serverCapability());

        try {
            session.sendMessage(new TextMessage(JsonUtil.toJson(resp)));
            if(message.getFeatures() != null && message.getFeatures().getMcp()) {
                //如果客户端开启mcp协议，异步初始化MCP工具
                ChatSession chatSession = sessionManager.getSession(sessionId);
                Thread.startVirtualThread(() -> {
                    DeviceBO device = chatSession != null ? chatSession.getDevice() : null;
                    if (device != null && device.getRoleId() != null) {
                        deviceMcpService.initialize(chatSession);
                    }
                });
            }
        } catch (Exception e) {
            log.error("发送hello响应失败", e);
        }
    }

    /**
     * 未声明或声明了不支持的版本时按 v1 裸帧处理
     */
    private int resolveProtocolVersion(Integer declared, String sessionId) {
        if (declared == null) {
            return BinaryProtocolCodec.VERSION_V1;
        }
        if (!BinaryProtocolCodec.isSupported(declared)) {
            log.warn("设备声明了不支持的协议版本v{}，按v1处理 - SessionId: {}", declared, sessionId);
            return BinaryProtocolCodec.VERSION_V1;
        }
        return declared;
    }

    private static Integer parseVersion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, String> getHeadersFromSession(WebSocketSession session) {
        // 设备标识只认 device-id，与握手鉴权拦截器保持一致
        String[] deviceKeys = { "device-id", "Authorization" };

        Map<String, String> headers = new HashMap<>();

        for (String key : deviceKeys) {
            String value = session.getHandshakeHeaders().getFirst(key);
            if (value != null) {
                headers.put(key, value);
            }
        }
        // 尝试从URI参数中获取
        URI uri = session.getUri();
        if (uri != null) {
            String query = uri.getQuery();
            if (query != null) {
                for (String key : deviceKeys) {
                    String paramPattern = key + "=";
                    int startIdx = query.indexOf(paramPattern);
                    if (startIdx >= 0) {
                        startIdx += paramPattern.length();
                        int endIdx = query.indexOf('&', startIdx);
                        headers.put(key, endIdx >= 0 ? query.substring(startIdx, endIdx) : query.substring(startIdx));
                    }
                }
            }
        }
        return headers;
    }
}

package com.xiaozhi.communication.server.websocket;

import com.xiaozhi.communication.common.*;
import com.xiaozhi.communication.domain.*;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.WebSocketSession;
import java.io.IOException;
import java.nio.ByteBuffer;

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

    /**
     * 单条下行消息的最长发送时间，超过即断开该连接。
     * 字段初值供未经 Spring 装配的调用方使用，容器内由配置覆盖。
     */
    @Value("${websocket.async-send-timeout:5000}")
    private int sendTimeLimitMs = 5000;

    /**
     * 单会话下行待发缓冲上限，超过按最旧优先丢弃。
     */
    @Value("${websocket.send-buffer-size-limit:524288}")
    private int sendBufferSizeLimit = 512 * 1024;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 设备身份只认握手拦截器写入的属性，不从请求头或 query 二次解析
        String deviceIdAuth = (String) session.getAttributes()
                .get(DeviceAuthHandshakeInterceptor.ATTR_DEVICE_ID);
        if (!StringUtils.hasText(deviceIdAuth)) {
            log.error("设备ID为空");
            try {
                session.close(CloseStatus.BAD_DATA.withReason("设备ID为空"));
            } catch (IOException e) {
                log.error("关闭WebSocket连接失败", e);
            }
            return;
        }

        // 播放线程、容器线程、工具调用线程会同时下行，原生 session 禁止并发写，
        // 装饰器负责串行化；缓冲溢出丢最旧帧而不是断连，避免弱网直接掉线
        com.xiaozhi.communication.server.websocket.WebSocketSession xiaoZhiSession
                = new com.xiaozhi.communication.server.websocket.WebSocketSession(
                        new ConcurrentWebSocketSessionDecorator(session, sendTimeLimitMs, sendBufferSizeLimit,
                                ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP));
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
            if (msg == null) {
                // 非 JSON、字段类型不匹配等硬性解析失败：整条丢弃，不进设备绑定、不进业务分发
                log.warn("无法解析设备消息，已丢弃 - SessionId: {}", sessionId);
                return;
            }
            if (msg instanceof HelloMessage m) {
                handleHelloMessage(session, m);
            } else if (msg instanceof PingMessage || msg instanceof UnknownMessage) {
                // 保活报文/未识别的 type：不进设备绑定、不进业务分发、不回应答
                return;
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
        if (exception instanceof java.io.EOFException) {
            return true;
        }
        if (!(exception instanceof IOException)) {
            return false;
        }
        String message = exception.getMessage();
        return message != null && (
                message.contains("Connection reset") ||
                message.contains("Broken pipe") ||
                message.contains("Connection closed") ||
                message.contains("远程主机强迫关闭了一个现有的连接"));
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
        var resp = new HelloResponseMessage()
                .setVersion(protocolVersion)
                .setTransport("websocket")
                .setSessionId(sessionId)
                .setAudioParams(AudioParams.serverCapability());

        try {
            // 走会话对象下行，直接写原生 session 会绕过串行化装饰器
            if (current == null) {
                log.warn("会话未注册，hello响应无法下发 - SessionId: {}", sessionId);
                return;
            }
            current.sendTextMessage(JsonUtil.toJson(resp));
            if (message.getFeatures() != null && Boolean.TRUE.equals(message.getFeatures().getMcp())) {
                //如果客户端开启mcp协议，异步初始化MCP工具
                Thread.startVirtualThread(() -> {
                    DeviceBO device = current.getDevice();
                    if (device != null && device.getRoleId() != null) {
                        deviceMcpService.initialize(current);
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

}

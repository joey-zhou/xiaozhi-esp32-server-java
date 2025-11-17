package com.xiaozhi.communication.server.websocket;

import com.xiaozhi.communication.common.*;
import com.xiaozhi.communication.domain.*;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpService;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.event.ChatAudioOpenEvent;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class WebSocketHandler extends AbstractWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);

    @Resource
    private SessionManager sessionManager;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private SysDeviceService deviceService;

    @Resource
    private DeviceMcpService deviceMcpService;

    @Resource
    private ApplicationContext applicationContext;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, String> headers = getHeadersFromSession(session);
        String deviceIdAuth = headers.get("device-id");
        String token = headers.get("Authorization");
        if (deviceIdAuth == null || deviceIdAuth.isEmpty()) {
            logger.error("设备ID为空");
            try {
                session.close(CloseStatus.BAD_DATA.withReason("设备ID为空"));
            } catch (IOException e) {
                logger.error("关闭WebSocket连接失败", e);
            }
            return;
        }
//        if (token != null && !token.isEmpty()) {//优先使用token判断
//            token = token.replace("Bearer ", "");
//            SysDevice device = new SysDevice();
//            device.setCode(token);
//            device.setDeviceId(deviceIdAuth);
//            device.setCreateTime(DateUtil.offsetMonth(DateUtil.date(), -1));//设置过期时间，目前给一个月有效期
//            if(sysDeviceService.queryVerifyCode(device) == null){//没有有效token
//                logger.error("设备提供的token不正确");
//                try {
//                    session.close(CloseStatus.BAD_DATA.withReason("设备提供的token不正确"));
//                } catch (IOException e) {
//                    logger.error("关闭WebSocket连接失败", e);
//                }
//                return;
//            }
//        }else{

        com.xiaozhi.communication.server.websocket.WebSocketSession xiaoZhiSession
                = new com.xiaozhi.communication.server.websocket.WebSocketSession(session);
        messageHandler.afterConnection(xiaoZhiSession, deviceIdAuth);
        applicationContext.publishEvent(new ChatAudioOpenEvent(xiaoZhiSession));
        logger.info("WebSocket连接建立成功 - SessionId: {}, DeviceId: {}", session.getId(), deviceIdAuth);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        SysDevice device = sessionManager.getDeviceConfig(sessionId);
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
                    device = sessionManager.getDeviceConfig(sessionId);
                    if (device == null || device.getRoleId() == null) {
                        logger.warn("自动绑定后设备信息异常 - SessionId: {}", sessionId);
                        return;
                    }
                    logger.info("自动绑定成功，继续处理消息 - SessionId: {}, DeviceId: {}", sessionId, device.getDeviceId());
                }
                messageHandler.handleMessage(msg, sessionId);
            }
        } catch (Exception e) {
            logger.error("handleTextMessage处理失败", e);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String sessionId = session.getId();
        SysDevice device = sessionManager.getDeviceConfig(sessionId);
        if (device == null) {
            return;
        }
        messageHandler.handleBinaryMessage(sessionId, message.getPayload().array());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        messageHandler.afterConnectionClosed(sessionId);
        logger.info("WebSocket连接关闭 - SessionId: {}, 状态: {}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = session.getId();
        // 检查是否是客户端正常关闭连接导致的异常
        if (isClientCloseRequest(exception)) {
            // 客户端主动关闭，记录为信息级别日志而非错误
            logger.info("WebSocket连接被客户端主动关闭 - SessionId: {}", sessionId);
            messageHandler.afterConnectionClosed(sessionId);
        } else {
            // 真正的传输错误
            logger.error("WebSocket传输错误 - SessionId: {}", sessionId, exception);
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
        logger.info("收到hello消息 - SessionId: {}, JsonNode: {}", sessionId, message);

        if (message.getAudioParams() != null) {
            logger.info("客户端音频参数 - 格式: {}, 采样率: {}, 声道: {}, 帧时长: {}ms",
                    message.getAudioParams().getFormat(),
                    message.getAudioParams().getSampleRate(),
                    message.getAudioParams().getChannels(),
                    message.getAudioParams().getFrameDuration());
        }

        // 回复hello消息
        var resp = new HelloMessageResp()
                .setTransport("websocket")
                .setSessionId(sessionId)
                .setAudioParams(AudioParams.Opus);

        try {
            session.sendMessage(new TextMessage(JsonUtil.toJson(resp)));
            if(message.getFeatures() != null && message.getFeatures().getMcp()) {
                //如果客户端开启mcp协议，异步初始化MCP工具
                ChatSession chatSession = sessionManager.getSession(sessionId);
                Thread.startVirtualThread(() -> {
                    SysDevice device = sessionManager.getDeviceConfig(sessionId);
                    if (device.getRoleId() != null) {
                        deviceMcpService.initialize(chatSession);
                    }
                });
            }
        } catch (Exception e) {
            logger.error("发送hello响应失败", e);
        }
    }

    private Map<String, String> getHeadersFromSession(WebSocketSession session) {
        // 尝试从请求头获取设备ID
        String[] deviceKeys = { "device-id", "mac_address", "uuid", "Authorization" };

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

package com.xiaozhi.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.websocket.llm.LlmManager;
import com.xiaozhi.websocket.service.AudioService;
import com.xiaozhi.websocket.service.MessageService;
import com.xiaozhi.websocket.service.SpeechToTextService;
import com.xiaozhi.websocket.service.TextToSpeechService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private SysDeviceService deviceService;

    @Autowired
    private SysConfigService configService;

    @Autowired
    private AudioService audioService;

    @Autowired
    private LlmManager llmManager;

    @Resource
    private MessageService messageService;

    @Autowired
    private TextToSpeechService textToSpeechService;

    @Autowired
    private SpeechToTextService speechToTextService;

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);

    // 用于存储所有连接的会话
    private static final ConcurrentHashMap<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    // 用于存储会话和设备的映射关系
    private static final ConcurrentHashMap<String, SysDevice> DEVICES_CONFIG = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, SysConfig> CONFIG = new ConcurrentHashMap<>();

    // 用于跟踪会话是否处于监听状态
    private static final ConcurrentHashMap<String, Boolean> LISTENING_STATE = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        SESSIONS.put(sessionId, session);
        // 初始化监听状态为false
        LISTENING_STATE.put(sessionId, false);

        Map<String, List<String>> headers = session.getHandshakeHeaders();

        // 从请求头中获取设备ID
        List<String> deviceIds = headers.get("device-id");
        String deviceId = ObjectUtils.isEmpty(deviceIds) ? "web_test_device": deviceIds.get(0);

        List<SysDevice> devices = deviceService.query(new SysDevice().setDeviceId(deviceId));
        SysDevice device;
        if (devices.isEmpty()) {
            device = new SysDevice();
            device.setDeviceId(deviceId);
            device.setSessionId(sessionId);
        } else {
            device = devices.get(0);
            device.setSessionId(sessionId);
            if (device.getSttId() != null) {
                CONFIG.put(device.getSttId(), configService.selectConfigById(device.getSttId()));
            }
            if (device.getTtsId() != null) {
                CONFIG.put(device.getTtsId(), configService.selectConfigById(device.getTtsId()));
            }
        }
        DEVICES_CONFIG.put(sessionId, device);
        logger.info("WebSocket连接建立成功 - SessionId: {}, DeviceId: {}", sessionId, deviceId);

        deviceService
                .update(new SysDevice().setDeviceId(device.getDeviceId()).setState("1")
                        .setLastLogin(new Date().toString()));

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        SysDevice device = DEVICES_CONFIG.get(sessionId);
        String payload = message.getPayload();

        // 解析JSON消息
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String messageType = jsonNode.path("type").asText();

            // 如果是hello消息，直接处理握手，不进行设备绑定验证
            if ("hello".equals(messageType)) {
                handleHelloMessage(session, jsonNode);
                return;
            }

            // 对于非hello消息，进行设备绑定验证
            List<SysDevice> deviceResult = deviceService
                    .query(new SysDevice().setDeviceId(device.getDeviceId()).setSessionId(sessionId));

            if (deviceResult.isEmpty()) {
                SysDevice codeResult = deviceService.generateCode(device);
                String audioFilePath;
                if (!StringUtils.hasText(codeResult.getAudioPath())) {
                    audioFilePath = textToSpeechService.textToSpeech("请到设备管理页面添加设备，输入验证码" + codeResult.getCode());
                    codeResult.setDeviceId(device.getDeviceId());
                    codeResult.setSessionId(sessionId);
                    codeResult.setAudioPath(audioFilePath);
                    deviceService.updateCode(codeResult);
                } else {
                    audioFilePath = codeResult.getAudioPath();
                }
                logger.info("设备未绑定，返回验证码");
                audioService.sendAudio(session, audioFilePath, codeResult.getCode());
                return;
            }

            // 处理其他类型的消息
            switch (messageType) {
                case "listen":
                    handleListenMessage(session, jsonNode);
                    break;
                case "abort":
                    handleAbortMessage(session, jsonNode);
                    break;
                case "iot":
                    handleIotMessage(session, jsonNode);
                    break;
                default:
                    logger.warn("未知的消息类型: {}", messageType);
                    break;
            }
        } catch (Exception e) {
            logger.error("处理消息失败", e);
        }
    }

    // 处理客户端发送的音频
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String sessionId = session.getId();
        SysDevice device = DEVICES_CONFIG.get(sessionId);
        SysConfig sttConfig = (device.getSttId() != null) ? CONFIG.get(device.getSttId()) : null;
        SysConfig ttsConfig = (device.getTtsId() != null) ? CONFIG.get(device.getTtsId()) : null;

        // 检查会话是否处于监听状态，如果不是则忽略音频数据
        Boolean isListening = LISTENING_STATE.getOrDefault(sessionId, false);
        if (!isListening) {
            return;
        }

        byte[] opusData = message.getPayload().array();
        try {
            // 将所有音频处理逻辑委托给AudioService
            byte[] completeAudio = audioService.processIncomingAudio(sessionId, opusData);

            if (completeAudio != null) {
                logger.info("检测到语音结束 - SessionId: {}, 音频大小: {} 字节", sessionId, completeAudio.length);
                String result;
                // 调用 SpeechToTextService 进行语音识别
                if (!ObjectUtils.isEmpty(sttConfig)) {
                    result = speechToTextService.recognition(completeAudio, sttConfig);
                } else {
                    String jsonResult = speechToTextService.recognition(completeAudio);
                    JsonNode resultNode = objectMapper.readTree(jsonResult);
                    result = resultNode.path("text").asText("");
                }
                if (StringUtils.hasText(result)) {
                    logger.info("语音识别结果 - SessionId: {}, 内容: {}", sessionId, result);
                    // 设置会话为非监听状态，防止处理自己的声音
                    LISTENING_STATE.put(sessionId, false);
                    messageService.sendMessage(session, "stt", "start", result);
                    // 使用句子切分处理流式响应
                    llmManager.chatStreamBySentence(device, result, (sentence, isStart, isEnd) -> {
                        try {
                            String audioPath = textToSpeechService.textToSpeech(sentence, ttsConfig,
                                    device.getVoiceName());
                            audioService.sendAudio(session, audioPath, sentence, isStart, isEnd);
                        } catch (Exception e) {
                            logger.error("处理句子失败: {}", e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            logger.error("处理二进制消息失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        SysDevice device = DEVICES_CONFIG.get(sessionId);

        // 更新设备在线时间
        if (device != null) {
            deviceService
                    .update(new SysDevice().setDeviceId(device.getDeviceId()).setState("0")
                            .setLastLogin(new Date().toString()));

            logger.info("WebSocket连接关闭 - SessionId: {}, DeviceId: {}, Status: {}", sessionId, device.getDeviceId(),
                    status);
        }

        SESSIONS.remove(sessionId);
        LISTENING_STATE.remove(sessionId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket传输错误 - SessionId: {}", session.getId(), exception);
    }

    // 处理客户端发送的hello消息
    private void handleHelloMessage(WebSocketSession session, JsonNode jsonNode) throws IOException {
        logger.info("收到hello消息 - SessionId: {}", session.getId());

        // 验证客户端hello消息
        /*if (!jsonNode.path("transport").asText().equals("websocket")) {
            logger.warn("不支持的传输方式: {}", jsonNode.path("transport").asText());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }*/

        // 解析音频参数
        JsonNode audioParams = jsonNode.path("audio_params");
        String format = audioParams.path("format").asText();
        int sampleRate = audioParams.path("sample_rate").asInt();
        int channels = audioParams.path("channels").asInt();
        int frameDuration = audioParams.path("frame_duration").asInt();

        logger.info("客户端音频参数 - 格式: {}, 采样率: {}, 声道: {}, 帧时长: {}ms",
                format, sampleRate, channels, frameDuration);

        // 回复hello消息
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "hello");
        response.put("transport", "websocket");
        response.put("session_id", session.getId());

        // 添加音频参数（可以根据服务器配置调整）
        ObjectNode responseAudioParams = response.putObject("audio_params");
        responseAudioParams.put("format", format);
        responseAudioParams.put("sample_rate", sampleRate);
        responseAudioParams.put("channels", channels);
        responseAudioParams.put("frame_duration", frameDuration);

        session.sendMessage(new TextMessage(response.toString()));
    }

    // 处理客户端发送的listen消息
    private void handleListenMessage(WebSocketSession session, JsonNode jsonNode) {
        String sessionId = session.getId();
        SysDevice device = DEVICES_CONFIG.get(sessionId);
        SysConfig sttConfig = (device.getSttId() != null) ? CONFIG.get(device.getSttId()) : null;
        SysConfig ttsConfig = (device.getTtsId() != null) ? CONFIG.get(device.getTtsId()) : null;
        // 解析listen消息中的state和mode字段
        String state = jsonNode.path("state").asText();
        String mode = jsonNode.path("mode").asText();

        logger.info("收到listen消息 - SessionId: {}, State: {}, Mode: {}", sessionId, state, mode);
        // 根据state处理不同的监听状态
        switch (state) {
            case "start":
                // 开始监听，准备接收音频数据
                logger.info("开始监听 - Mode: {}", mode);
                LISTENING_STATE.put(sessionId, true);
                break;
            case "stop":
                // 停止监听
                logger.info("停止监听");
                LISTENING_STATE.put(sessionId, false);
                break;
            case "detect":
                // 检测到唤醒词
                String text = jsonNode.path("text").asText();
                logger.info("检测到唤醒词: {}", text);
                // 设置为非监听状态，防止处理自己的声音
                LISTENING_STATE.put(sessionId, false);
                messageService.sendMessage(session, "stt", "start", text);
                // 使用句子切分处理流式响应
                llmManager.chatStreamBySentence(device, text, (sentence, isStart, isEnd) -> {
                    try {
                        String audioPath = textToSpeechService.textToSpeech(sentence, ttsConfig,
                                device.getVoiceName());
                        audioService.sendAudio(session, audioPath, sentence, isStart, isEnd);
                    } catch (Exception e) {
                        logger.error("处理句子失败: {}", e.getMessage(), e);
                    }
                });
                break;
            default:
                logger.warn("未知的listen状态: {}", state);
                break;
        }
    }

    // 处理客户端发送的abort消息
    private void handleAbortMessage(WebSocketSession session, JsonNode jsonNode) {
        String sessionId = session.getId();
        String reason = jsonNode.path("reason").asText();

        logger.info("收到abort消息 - SessionId: {}, Reason: {}", sessionId, reason);

        // 终止语音发送
        audioService.sendStop(session);
    }

    // 处理客户端发送的IoT消息
    private void handleIotMessage(WebSocketSession session, JsonNode jsonNode) {
        String sessionId = session.getId();
        logger.info("收到IoT消息 - SessionId: {}", sessionId);

        // 处理设备描述信息
        if (jsonNode.has("descriptors")) {
            JsonNode descriptors = jsonNode.path("descriptors");
            logger.info("收到设备描述信息: {}", descriptors);
            // 处理设备描述信息的逻辑
        }

        // 处理设备状态更新
        if (jsonNode.has("states")) {
            JsonNode states = jsonNode.path("states");
            logger.info("收到设备状态更新: {}", states);
            // 处理设备状态更新的逻辑
        }
    }
}
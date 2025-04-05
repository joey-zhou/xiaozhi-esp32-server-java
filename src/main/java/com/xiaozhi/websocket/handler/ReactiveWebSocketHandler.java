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
import com.xiaozhi.websocket.stt.factory.SttServiceFactory;
import com.xiaozhi.websocket.service.TextToSpeechService;
import com.xiaozhi.websocket.service.VadService;
import com.xiaozhi.websocket.service.VadService.VadStatus;
import com.xiaozhi.websocket.stt.SttService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReactiveWebSocketHandler implements WebSocketHandler {

    @Autowired
    private SysDeviceService deviceService;

    @Autowired
    private SysConfigService configService;

    @Autowired
    private AudioService audioService;

    @Autowired
    private LlmManager llmManager;

    @Autowired
    private MessageService messageService;

    @Autowired
    private TextToSpeechService textToSpeechService;

    @Autowired
    private SttServiceFactory sttServiceFactory;

    @Autowired
    private VadService vadService;

    private static final Logger logger = LoggerFactory.getLogger(ReactiveWebSocketHandler.class);

    // 用于存储所有连接的会话
    private static final ConcurrentHashMap<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    // 用于存储会话和设备的映射关系
    private static final ConcurrentHashMap<String, SysDevice> DEVICES_CONFIG = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, SysConfig> CONFIG = new ConcurrentHashMap<>();

    // 用于跟踪会话是否处于监听状态
    private static final ConcurrentHashMap<String, Boolean> LISTENING_STATE = new ConcurrentHashMap<>();

    // 用于存储每个会话的音频数据流
    private static final ConcurrentHashMap<String, Sinks.Many<byte[]>> AUDIO_SINKS = new ConcurrentHashMap<>();

    // 用于跟踪会话是否正在进行流式识别
    private static final ConcurrentHashMap<String, Boolean> STREAMING_STATE = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        SESSIONS.put(sessionId, session);
        LISTENING_STATE.put(sessionId, false);
        STREAMING_STATE.put(sessionId, false);

        logger.info(session.getHandshakeInfo().getHeaders().toString());

        // 从请求头中获取设备ID
        String deviceId = session.getHandshakeInfo().getHeaders().getFirst("device-Id");
        if (deviceId == null) {
            logger.error("设备ID为空");
            return session.close();
        }

        return Mono.fromCallable(() -> deviceService.query(new SysDevice().setDeviceId(deviceId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(devices -> {
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

                    // 更新设备状态
                    return Mono.fromRunnable(() -> deviceService.update(new SysDevice()
                            .setDeviceId(device.getDeviceId())
                            .setState("1")
                            .setLastLogin(new Date().toString()))).subscribeOn(Schedulers.boundedElastic()).then();
                })
                .then(
                        // 处理接收到的消息
                        session.receive()
                                .flatMap(message -> {
                                    if (message.getType() == WebSocketMessage.Type.TEXT) {
                                        return handleTextMessage(session, message);
                                    } else if (message.getType() == WebSocketMessage.Type.BINARY) {
                                        return handleBinaryMessage(session, message);
                                    }
                                    return Mono.empty();
                                })
                                .onErrorResume(e -> {
                                    logger.error("处理WebSocket消息失败", e);
                                    return Mono.empty();
                                })
                                .then())
                .doFinally(signal -> {
                    // 连接关闭时清理资源
                    SysDevice device = DEVICES_CONFIG.get(sessionId);
                    if (device != null) {
                        deviceService.update(new SysDevice()
                                .setDeviceId(device.getDeviceId())
                                .setState("0")
                                .setLastLogin(new Date().toString()));
                        logger.info("WebSocket连接关闭 - SessionId: {}, DeviceId: {}", sessionId, device.getDeviceId());
                    }

                    SESSIONS.remove(sessionId);
                    DEVICES_CONFIG.remove(sessionId);
                    LISTENING_STATE.remove(sessionId);
                    STREAMING_STATE.remove(sessionId);

                    // 清理音频流
                    Sinks.Many<byte[]> sink = AUDIO_SINKS.remove(sessionId);
                    if (sink != null) {
                        sink.tryEmitComplete();
                    }

                    // 清理VAD会话
                    vadService.resetSession(sessionId);

                    // 清理音频处理会话
                    audioService.cleanupSession(sessionId);
                });
    }

    private Mono<Void> handleTextMessage(WebSocketSession session, WebSocketMessage message) {
        String sessionId = session.getId();
        SysDevice device = DEVICES_CONFIG.get(sessionId);
        String payload = message.getPayloadAsText();

        try {
            // 首先尝试解析JSON消息
            JsonNode jsonNode = objectMapper.readTree(payload);
            String messageType = jsonNode.path("type").asText();

            // hello消息应该始终处理，无论设备是否绑定
            if ("hello".equals(messageType)) {
                return handleHelloMessage(session, jsonNode);
            }

            // 对于其他消息类型，需要检查设备是否已绑定
            return Mono.fromCallable(
                    () -> deviceService
                            .query(new SysDevice().setDeviceId(device.getDeviceId()).setSessionId(sessionId)))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(deviceResult -> {
                        if (deviceResult.isEmpty()) {
                            // 设备未绑定，处理未绑定设备的消息
                            return handleUnboundDevice(session, device);
                        } else {
                            // 设备已绑定，根据消息类型处理
                            switch (messageType) {
                                case "listen":
                                    return handleListenMessage(session, jsonNode);
                                case "abort":
                                    return handleAbortMessage(session, jsonNode);
                                case "iot":
                                    return handleIotMessage(session, jsonNode);
                                default:
                                    logger.warn("未知的消息类型: {}", messageType);
                                    return Mono.empty();
                            }
                        }
                    });
        } catch (Exception e) {
            logger.error("处理文本消息失败", e);
            return Mono.empty();
        }
    }

    private Mono<Void> handleBinaryMessage(WebSocketSession session, WebSocketMessage message) {
        String sessionId = session.getId();
        SysDevice device = DEVICES_CONFIG.get(sessionId);
        SysConfig sttConfig = (device.getSttId() != null) ? CONFIG.get(device.getSttId()) : null;
        SysConfig ttsConfig = (device.getTtsId() != null) ? CONFIG.get(device.getTtsId()) : null;

        // 检查会话是否处于监听状态，如果不是则忽略音频数据
        Boolean isListening = LISTENING_STATE.getOrDefault(sessionId, false);
        if (!isListening) {
            return Mono.empty();
        }

        // 获取二进制数据
        DataBuffer dataBuffer = message.getPayload();
        DataBuffer retainedBuffer = DataBufferUtils.retain(dataBuffer);
        byte[] opusData = new byte[retainedBuffer.readableByteCount()];
        retainedBuffer.read(opusData);
        DataBufferUtils.release(retainedBuffer);

        return Mono.fromCallable(() -> {
            // 使用VAD处理音频数据，获取语音活动状态和处理后的PCM数据
            return vadService.processAudio(sessionId, opusData);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(vadResult -> {
                    // 如果VAD处理出错，直接返回
                    if (vadResult.getStatus() == VadStatus.ERROR || vadResult.getProcessedData() == null) {
                        return Mono.empty();
                    }

                    // 检查是否支持流式处理
                    boolean supportsStreaming = true;

                    // 根据VAD状态处理
                    switch (vadResult.getStatus()) {
                        case SPEECH_START:
                            // 检测到语音开始，初始化流式识别
                            if (supportsStreaming) {
                                return initializeStreamingRecognition(session, sessionId, sttConfig, ttsConfig, device,
                                        vadResult.getProcessedData());
                            }
                            return Mono.empty();

                        case SPEECH_CONTINUE:
                            // 语音继续，发送数据到流式识别
                            if (supportsStreaming) {
                                Sinks.Many<byte[]> audioSink = AUDIO_SINKS.get(sessionId);
                                if (audioSink != null && STREAMING_STATE.getOrDefault(sessionId, false)) {
                                    audioSink.tryEmitNext(vadResult.getProcessedData());
                                }
                            }
                            return Mono.empty();

                        case SPEECH_END:
                            // 语音结束，完成流式识别
                            if (supportsStreaming) {
                                Sinks.Many<byte[]> audioSink = AUDIO_SINKS.get(sessionId);
                                if (audioSink != null) {
                                    audioSink.tryEmitComplete();
                                    STREAMING_STATE.put(sessionId, false);
                                }
                            }
                            return Mono.empty();

                        default:
                            return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    logger.error("处理音频数据失败: {}", e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * 初始化流式语音识别
     */
    private Mono<Void> initializeStreamingRecognition(
            WebSocketSession session,
            String sessionId,
            SysConfig sttConfig,
            SysConfig ttsConfig,
            SysDevice device,
            byte[] initialAudio) {

        // 如果已经在进行流式识别，先清理旧的资源
        Sinks.Many<byte[]> existingSink = AUDIO_SINKS.get(sessionId);
        if (existingSink != null) {
            existingSink.tryEmitComplete();
        }

        // 创建新的音频数据接收器
        Sinks.Many<byte[]> audioSink = Sinks.many().multicast().onBackpressureBuffer();
        AUDIO_SINKS.put(sessionId, audioSink);
        STREAMING_STATE.put(sessionId, true);

        // 获取对应的STT服务
        SttService sttService = sttServiceFactory.getSttService(sttConfig);

        if (sttService == null) {
            logger.error("无法获取STT服务 - Provider: {}", sttConfig != null ? sttConfig.getProvider() : "null");
            return Mono.empty();
        }

        logger.info("初始化流式识别 - Provider: {}, SessionId: {}", sttService.getProviderName(), sessionId);

        // 发送初始音频数据
        if (initialAudio != null && initialAudio.length > 0) {
            audioSink.tryEmitNext(initialAudio);
        }

        // 启动流式识别
        sttService.streamRecognition(audioSink.asFlux())
                .doOnNext(text -> {
                    // 发送中间识别结果
                    if (StringUtils.hasText(text)) {
                        messageService.sendMessage(session, "stt", "interim", text).subscribe();
                    }
                })
                .last() // 获取最终结果
                .flatMap(finalText -> {
                    if (!StringUtils.hasText(finalText)) {
                        return Mono.empty();
                    }

                    logger.info("流式识别最终结果 - SessionId: {}, 内容: {}", sessionId, finalText);

                    // 设置会话为非监听状态，防止处理自己的声音
                    LISTENING_STATE.put(sessionId, false);

                    // 发送最终识别结果
                    return messageService.sendMessage(session, "stt", "final", finalText)
                            .then(Mono.fromRunnable(() -> {
                                // 使用句子切分处理流式响应
                                llmManager.chatStreamBySentence(device, finalText,
                                        (sentence, isStart, isEnd) -> {
                                            Mono.fromCallable(() -> textToSpeechService.textToSpeech(
                                                    sentence, ttsConfig, device.getVoiceName()))
                                                    .subscribeOn(Schedulers.boundedElastic())
                                                    .flatMap(audioPath -> audioService
                                                            .sendAudioMessage(session, audioPath, sentence, isStart,
                                                                    isEnd)
                                                            .doOnError(e -> logger.error("发送音频消息失败: {}", e.getMessage(),
                                                                    e)))
                                                    .onErrorResume(e -> {
                                                        logger.error("处理句子失败: {}", e.getMessage(), e);
                                                        return Mono.empty();
                                                    })
                                                    .subscribe();
                                        });
                            }));
                })
                .onErrorResume(error -> {
                    logger.error("流式识别错误: {}", error.getMessage(), error);
                    return Mono.empty();
                })
                .subscribe();

        return Mono.empty();
    }

    private Mono<Void> handleUnboundDevice(WebSocketSession session, SysDevice device) {
        return Mono.fromCallable(() -> {
            SysDevice codeResult = deviceService.generateCode(device);
            String audioFilePath;
            if (!StringUtils.hasText(codeResult.getAudioPath())) {
                audioFilePath = textToSpeechService.textToSpeech("请到设备管理页面添加设备，输入验证码" + codeResult.getCode());
                codeResult.setDeviceId(device.getDeviceId());
                codeResult.setSessionId(session.getId());
                codeResult.setAudioPath(audioFilePath);
                deviceService.updateCode(codeResult);
            } else {
                audioFilePath = codeResult.getAudioPath();
            }
            logger.info("设备未绑定，返回验证码");
            return audioService.sendAudioMessage(session, audioFilePath, codeResult.getCode(), true, true);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<Void> handleHelloMessage(WebSocketSession session, JsonNode jsonNode) {
        logger.info("收到hello消息 - SessionId: {}", session.getId());

        // 验证客户端hello消息
        if (!jsonNode.path("transport").asText().equals("websocket")) {
            logger.warn("不支持的传输方式: {}", jsonNode.path("transport").asText());
            return session.close();
        }

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

        // 添加音频参数（可以根据服务器配置调整）
        ObjectNode responseAudioParams = response.putObject("audio_params");
        responseAudioParams.put("format", format);
        responseAudioParams.put("sample_rate", sampleRate);
        responseAudioParams.put("channels", channels);
        responseAudioParams.put("frame_duration", frameDuration);

        return session.send(Mono.just(session.textMessage(response.toString())));
    }

    private Mono<Void> handleListenMessage(WebSocketSession session, JsonNode jsonNode) {
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

                // 初始化VAD会话
                vadService.initializeSession(sessionId);

                return Mono.empty();
            case "stop":
                // 停止监听
                logger.info("停止监听");
                LISTENING_STATE.put(sessionId, false);

                // 如果正在进行流式识别，发送完成信号
                Sinks.Many<byte[]> audioSink = AUDIO_SINKS.get(sessionId);
                if (audioSink != null) {
                    audioSink.tryEmitComplete();
                    STREAMING_STATE.put(sessionId, false);
                }

                // 重置VAD会话
                vadService.resetSession(sessionId);

                return Mono.empty();
            case "detect":
                // 检测到唤醒词
                String text = jsonNode.path("text").asText();
                logger.info("检测到唤醒词: {}", text);

                // 设置为非监听状态，防止处理自己的声音
                LISTENING_STATE.put(sessionId, false);

                // 发送识别结果
                messageService.sendMessage(session, "stt", "start", text).subscribe();

                // 使用句子切分处理流式响应
                return Mono.fromRunnable(() -> {
                    // 使用句子切分处理流式响应
                    llmManager.chatStreamBySentence(device, text,
                            (sentence, isStart, isEnd) -> {
                                // 检查是否使用流式TTS
                                boolean useStreamTts = ttsConfig != null && "true".equals(ttsConfig.getConfigDesc());

                                if (useStreamTts) {
                                    // 使用流式TTS处理
                                    audioService
                                            .streamAudioMessage(session, sentence, isStart, isEnd, ttsConfig,
                                                    device.getVoiceName())
                                            .doOnError(e -> logger.error("流式发送音频消息失败: {}", e.getMessage(), e))
                                            .subscribe();
                                } else {
                                    // 使用原有的TTS处理方式
                                    Mono.fromCallable(() -> textToSpeechService.textToSpeech(
                                            sentence, ttsConfig, device.getVoiceName()))
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .flatMap(audioPath -> audioService
                                                    .sendAudioMessage(session, audioPath, sentence, isStart,
                                                            isEnd)
                                                    .doOnError(e -> logger.error("发送音频消息失败: {}", e.getMessage(),
                                                            e)))
                                            .onErrorResume(e -> {
                                                logger.error("处理句子失败: {}", e.getMessage(), e);
                                                return Mono.empty();
                                            })
                                            .subscribe();
                                }
                            });
                }).subscribeOn(Schedulers.boundedElastic()).then();
            default:
                logger.warn("未知的listen状态: {}", state);
                return Mono.empty();
        }
    }

    private Mono<Void> handleAbortMessage(WebSocketSession session, JsonNode jsonNode) {
        String sessionId = session.getId();
        String reason = jsonNode.path("reason").asText();

        logger.info("收到abort消息 - SessionId: {}, Reason: {}", sessionId, reason);

        // 如果正在进行流式识别，发送完成信号
        Sinks.Many<byte[]> audioSink = AUDIO_SINKS.get(sessionId);
        if (audioSink != null) {
            audioSink.tryEmitComplete();
            STREAMING_STATE.put(sessionId, false);
        }

        // 终止语音发送
        return audioService.sendStop(session);
    }

    private Mono<Void> handleIotMessage(WebSocketSession session, JsonNode jsonNode) {
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

        return Mono.empty();
    }
}
package com.xiaozhi.communication.common;

import com.xiaozhi.communication.domain.*;
import com.xiaozhi.communication.domain.mcp.device.initialize.DeviceMcpPayload;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.port.DeviceWriter;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.dialogue.DialogueService;
import com.xiaozhi.dialogue.audio.AecService;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.dialogue.llm.factory.PersonaFactory;
import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.llm.tool.device.IotService;
import com.xiaozhi.dialogue.audio.VadService;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.ScheduledPlayer;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.enums.ListenState;
import com.xiaozhi.event.ChatAbortedEvent;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.AudioUtils;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MessageHandler {
    @Resource
    private DeviceService deviceService;

    @Resource
    private DeviceWriter deviceWriter;

    @Resource
    private VadService vadService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private DialogueService dialogueService;

    @Resource
    private IotService iotService;

    @Resource
    private TtsServiceFactory ttsFactory;

    @Resource
    private PersonaFactory personaFactory;

    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private ToolsGlobalRegistry toolsGlobalRegistry;

    @Resource
    private RoleService roleService;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private MessageSender messageService;

    @Resource
    private AecService aecService;

    @Resource
    private DeviceRegistry deviceRegistry;

    @Resource
    private InstanceIdHolder instanceIdHolder;

    @Resource
    private RedisBroadcast redisBroadcast;

    @Resource
    private StorageServiceFactory storageServiceFactory;

    /** 验证码语音的存储目录，落在音频目录下单独一层，便于与对话录音区分 */
    private static final String VERIFY_CODE_AUDIO_DIR = "verifycode/";

    // 用于存储设备ID和验证码生成状态的映射
    private final Map<String, Boolean> captchaGenerationInProgress = new ConcurrentHashMap<>();

    /**
     * 处理连接建立事件.
     *
     * @param chatSession
     * @param deviceIdAuth
     */
    public void afterConnection(ChatSession chatSession, String deviceIdAuth) {
        String deviceId = deviceIdAuth;
        String sessionId = chatSession.getSessionId();
        // 注册会话
        sessionManager.registerSession(sessionId, chatSession);

        // 跨实例幽灵会话清理：如果设备之前绑定在其他实例，通知旧实例关闭会话。
        // 带上本次新会话的sessionId作为排除项，即使广播晚到、本实例已完成registerDevice，
        // 接收方也能识别出这就是刚建立的新会话而跳过，不依赖处理时序
        String previousInstance = deviceRegistry.getInstance(deviceId);
        if (previousInstance != null && !previousInstance.equals(instanceIdHolder.getInstanceId())) {
            log.info("设备 {} 之前在实例 {} 上，通知旧实例清理幽灵会话", deviceId, previousInstance);
            redisBroadcast.closeDeviceSession(deviceId, sessionId);
        }

        log.info("开始查询设备信息 - DeviceId: {}", deviceId);
        DeviceBO device = Optional.ofNullable(deviceService.getBO(deviceId)).orElse(new DeviceBO());
        device.setDeviceId(deviceId);
        device.setSessionId(sessionId);
        sessionManager.registerDevice(sessionId, device);
        // 如果已绑定，则初始化其他内容
        if (!ObjectUtils.isEmpty(device) && device.getRoleId() != null) {
            initializeBoundDevice(chatSession, device);
        }
    }

    /**
     * 初始化已绑定的设备
     *
     * @param chatSession 聊天会话
     * @param device 设备信息
     */
    private void initializeBoundDevice(ChatSession chatSession, DeviceBO device) {
        String deviceId = device.getDeviceId();
        String sessionId = chatSession.getSessionId();
        
        //这里需要放在虚拟线程外
        ToolsSessionHolder toolsSessionHolder = new ToolsSessionHolder(chatSession.getSessionId(),
                device, toolsGlobalRegistry);
        chatSession.setToolsSessionHolder(toolsSessionHolder);
        // 从缓存/数据库获取角色描述。device
        RoleBO role = roleService.getBO(device.getRoleId());
        if (role == null) {
            throw new IllegalStateException("角色不存在");
        }

        personaFactory.buildPersona(chatSession, device, role);

        // 连接建立时就初始化 AEC，确保后续任何 TTS 播放（含唤醒响应）的参考帧都不会被丢弃
        if (aecService != null) aecService.initSession(sessionId);

        // 以上同步处理结束后，异步更新设备在线状态
        String newState = DeviceBO.DEVICE_STATE_ONLINE;
        Thread.startVirtualThread(() -> {
            try {
                deviceWriter.updateState(deviceId, newState);
            } catch (Exception e) {
                // 仅记录告警，不关闭会话：状态写库失败不影响设备正常通信
                log.warn("更新设备在线状态失败 - DeviceId: {}, State: {}", deviceId, newState, e);
            }
        });
    }

    /**
     * 处理连接关闭事件.
     *
     * @param sessionId
     */
    public void afterConnectionClosed(String sessionId) {
        // VAD/AEC 由 closeSession 统一释放：会话可能已被 goodbye、超时告别等路径提前摘除，
        // 这里再按 sessionId 取一次会取到 null
        sessionManager.closeSession(sessionId);
    }

    /**
     * 处理音频数据
     *
     * @param sessionId
     * @param opusData
     */
    public void handleBinaryMessage(String sessionId, byte[] opusData) {
        // v1 裸 opus 帧无时间戳
        handleBinaryMessage(sessionId, opusData, 0);
    }

    /**
     * @param timestamp 设备回显的下行帧时间戳，0 表示无；供服务端 AEC 对齐使用
     */
    public void handleBinaryMessage(String sessionId, byte[] opusData, long timestamp) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if ((chatSession == null || !chatSession.isOpen()) && !vadService.isSessionInitialized(sessionId)) {
            return;
        }
        // 委托给DialogueService处理音频数据
        dialogueService.processAudioData(chatSession, opusData, timestamp);

    }

    /**
     * 处理未绑定设备
     * @return true 如果设备自动绑定成功，false 如果需要生成验证码
     */
    public boolean handleUnboundDevice(String sessionId, DeviceBO device) {
        String deviceId;
        if (device == null || device.getDeviceId() == null) {
            return false;
        }
        deviceId = device.getDeviceId();
        
        // 检查是否是 user_chat_ 开头的虚拟设备，如果是则自动绑定
        if (deviceId.startsWith("user_chat_")) {
            try {
                log.info("检测到虚拟设备 {}，尝试自动绑定", deviceId);
                
                // 提取用户ID
                String userIdStr = deviceId.substring("user_chat_".length());
                Integer userId = Integer.parseInt(userIdStr);
                
                RoleBO defaultRole = roleService.getDefaultOrFirstBO(userId);
                Integer defaultRoleId = defaultRole != null ? defaultRole.getRoleId() : null;
                
                if (defaultRoleId != null) {
                    // 创建虚拟设备并绑定到默认角色
                    deviceWriter.register(deviceId, "小助手", "web", userId, defaultRoleId);
                    log.info("虚拟设备 {} 自动绑定成功，角色ID: {}", deviceId, defaultRoleId);
                    
                    // 重新查询设备信息
                    DeviceBO boundDevice = deviceService.getBO(deviceId);
                    if (boundDevice != null) {
                        // 更新会话中的设备信息
                        boundDevice.setSessionId(sessionId);
                        sessionManager.registerDevice(sessionId, boundDevice);
                        
                        // 获取会话对象
                        ChatSession chatSession = sessionManager.getSession(sessionId);
                        if (chatSession != null && chatSession.isOpen()) {
                            // 初始化设备会话（与afterConnection中的逻辑一致）
                            initializeBoundDevice(chatSession, boundDevice);
                            log.info("虚拟设备 {} 初始化完成，可以开始对话", deviceId);
                        }
                        
                        // 设备已绑定并初始化完成，返回true表示可以继续处理消息
                        return true;
                    }
                } else {
                    log.warn("用户 {} 没有可用的角色，无法自动绑定虚拟设备", userId);
                }
            } catch (NumberFormatException e) {
                log.error("解析虚拟设备ID失败: {}", deviceId, e);
            } catch (Exception e) {
                log.error("自动绑定虚拟设备失败: {}", deviceId, e);
            }
        }
        
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if (chatSession == null || !chatSession.isOpen()) {
            return false;
        }
        // 检查是否已经在处理中，使用CAS操作保证线程安全
        Boolean previous = captchaGenerationInProgress.putIfAbsent(deviceId, true);
        if (previous != null && previous) {
            return false; // 已经在处理中
        }

        Thread.startVirtualThread(() -> {
            try {
                // 对于未绑定设备， 播放器是一次性用途，不需要绑定到ChatSession。
                Player player = new ScheduledPlayer(chatSession, messageService);
                // 设备已注册但未配置模型
                if (device.getDeviceName() != null && device.getRoleId() == null) {
                    String message = "设备未配置角色，请到角色配置页面完成配置后开始对话";

                    Path audioFilePath = ttsFactory.getDefaultTtsService().textToSpeech(message);
                    if (audioFilePath == null) {
                        // TTS 失败返回 null，标记立刻放开，设备下一条消息还能再试一次
                        log.error("未配置角色的提示语音合成失败，设备收不到提示 - DeviceId: {}", deviceId);
                        captchaGenerationInProgress.remove(deviceId);
                        return;
                    }

                    player.play(message, audioFilePath);

                    // 延迟一段时间后再解除标记
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    captchaGenerationInProgress.remove(deviceId);
                    return;
                }

                // 设备未命名，生成验证码
                // 生成新验证码
                VerifyCodeBO codeResult = deviceService.generateCode(deviceId, sessionId, device.getType());
                byte[] audioData;
                // 判断音频格式用，本次合成的取文件名，命中记录的取入库的存储路径
                String audioName = codeResult.getAudioPath();
                if (!StringUtils.hasText(audioName)) {
                    String codeMessage = "请到设备管理页面添加设备，输入验证码" + codeResult.getCode();
                    Path audioFile = ttsFactory.getDefaultTtsService().textToSpeech(codeMessage);
                    if (audioFile == null) {
                        // 合成失败就不要把空路径写进验证码记录，否则下次命中缓存分支会拿到脏数据
                        log.error("验证码语音合成失败，设备收不到验证码 - DeviceId: {}, Code: {}", deviceId, codeResult.getCode());
                        captchaGenerationInProgress.remove(deviceId);
                        return;
                    }
                    audioName = audioFile.getFileName().toString();
                    // 上传会接管本地文件（云端上传后即删），音频字节先读出来，本次播报不依赖上传结果
                    audioData = Files.readAllBytes(audioFile);
                    try {
                        // 这条记录由 server 进程读来下发前端、由本进程读来重播，
                        // 两边工作目录未必相同，必须交给存储服务，入库的是存储返回的路径
                        String storedPath = storageServiceFactory.getStorageService()
                                .upload(audioFile, AudioUtils.AUDIO_PATH + VERIFY_CODE_AUDIO_DIR + audioName);
                        deviceService.updateCodeAudioPath(deviceId, sessionId, codeResult.getCode(), storedPath);
                    } catch (Exception e) {
                        // 存不进共享存储就不落库，留空让下次重新合成，好过记下一个别处读不到的路径
                        log.error("验证码语音上传失败，本次仍向设备播报 - DeviceId: {}, Code: {}", deviceId, codeResult.getCode(), e);
                    }
                } else {
                    audioData = storageServiceFactory.downloadFrom(audioName);
                    if (audioData == null || audioData.length == 0) {
                        log.error("验证码语音读取失败，设备收不到验证码 - DeviceId: {}, AudioPath: {}", deviceId, audioName);
                        captchaGenerationInProgress.remove(deviceId);
                        return;
                    }
                }

                player.play(codeResult.getCode(), audioData, audioName);
                // 延迟一段时间后再解除标记
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                captchaGenerationInProgress.remove(deviceId);

            } catch (Exception e) {
                log.error("处理未绑定设备失败", e);
                captchaGenerationInProgress.remove(deviceId);
            }
        });
        
        // 返回false表示需要验证码流程，不继续处理当前消息
        return false;
    }

    /**
     * 记录设备是否要求服务端做 AEC。features.aec 只在设备端 AEC 关闭时才出现，未出现表示设备自己已消回声。
     */
    public void applyAecCapability(String sessionId, HelloMessage message) {
        if (aecService == null) {
            return;
        }
        boolean required = message.getFeatures() != null && Boolean.TRUE.equals(message.getFeatures().getAec());
        aecService.setServerAecRequired(sessionId, required);
    }

    /**
     * 记录设备声明的音频参数，与服务端固定的处理格式不一致时告警。
     * 服务端不按设备参数重配链路（Opus 编解码采样率无关，设备侧会自行重采样到硬件采样率）。
     */
    public void applyAudioParams(String sessionId, AudioParams deviceParams) {
        if (deviceParams == null) {
            return;
        }
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if (chatSession != null) {
            chatSession.setDeviceAudioParams(deviceParams);
        }
        log.info("客户端音频参数 - 格式: {}, 采样率: {}, 声道: {}, 帧时长: {}ms",
                deviceParams.getFormat(), deviceParams.getSampleRate(),
                deviceParams.getChannels(), deviceParams.getFrameDuration());
        String mismatch = deviceParams.mismatchAgainstServer();
        if (mismatch != null) {
            log.warn("设备音频参数与服务端不一致，可能影响识别或播放 - SessionId: {}, {}", sessionId, mismatch);
        }
    }

    private void handleListenMessage(ChatSession chatSession, ListenMessage message) {
        String sessionId = chatSession.getSessionId();
        log.info("收到listen消息 - SessionId: {}, State: {}, Mode: {}", sessionId, message.getState(), message.getMode());

        // 会话标记为即将关闭时忽略listen消息；player 已被告别流程清空时按没有待执行回调处理
        Player player = chatSession.getPlayer();
        if (player != null && player.getFunctionAfterChat() != null) {
            return;
        }

        // stop 消息不带 mode，无条件赋值会把本轮模式抹成 null
        if (message.getMode() != null) {
            chatSession.setMode(message.getMode());
        }

        // 根据state处理不同的监听状态
        switch (message.getState()) {
            case ListenState.Start:
                // 设备开始录音，进入聆听状态
                log.info("开始监听 - Mode: {}", message.getMode());

                chatSession.transitionTo(DeviceState.LISTENING);

                // manual 由客户端松手断句，服务端不做自动收句
                vadService.initSession(sessionId, chatSession.getMode() != ListenMode.Manual);
                // 初始化AEC会话
                if (aecService != null) aecService.initSession(sessionId);
                break;

            case ListenState.Stop:
                // 停止监听
                log.info("停止监听 - Mode: {}", chatSession.getMode());

                // audioSinks 在上一轮结束后仍非空，只有 VAD 本轮状态是准确信号
                if (chatSession.getMode() == ListenMode.Manual
                        && chatSession.getDeviceState() == DeviceState.LISTENING
                        && vadService.finishSegment(sessionId)) {
                    // 松手收句。不能 closeAudioStream/resetSession，STT 虚拟线程之后还要读 pcmData
                    dialogueService.completeSpeechSegment(chatSession);
                } else {
                    // 取消本次聆听，回到 IDLE
                    chatSession.closeAudioStream();
                    chatSession.transitionTo(DeviceState.IDLE);
                    vadService.resetSession(sessionId);
                    // 不重置 AEC 会话，保留已收敛的滤波器状态供后续对话复用
                }
                break;

            case ListenState.Text:
                // 检测聊天文本输入 — 确保 AEC 在 TTS 开始前已初始化
                if (aecService != null) aecService.initSession(sessionId);
                if (player != null ) {
                    String modeValue = message.getMode() != null ? message.getMode().getValue() : null;
                    String abortDeviceId = chatSession.getDevice() != null ? chatSession.getDevice().getDeviceId() : null;
                    applicationContext.publishEvent(new ChatAbortedEvent(this, chatSession.getSessionId(), abortDeviceId, modeValue));
                }
                // 回执按序留在读线程上发出；建 Persona 与启动 LLM 要走工具路由的 embedding、
                // RAG 的向量检索和一次 LLM 建连，占住读线程会让设备随后发来的 abort、listen stop 一直排队
                sessionManager.updateLastActivity(sessionId);
                messageService.sendSttMessage(chatSession, message.getText());
                log.info("处理聊天文字输入: \"{}\"", message.getText());
                Thread.startVirtualThread(() -> {
                    try {
                        personaFactory.buildPersona(chatSession);
                        dialogueService.handleText(chatSession, SttResult.textOnly(message.getText()));
                    } catch (Exception e) {
                        log.error("处理聊天文字输入失败 - SessionId: {}", sessionId, e);
                    }
                });
                break;

            case ListenState.Detect:
                // 检测到唤醒词 — 确保 AEC 在 TTS 开始前已初始化
                if (aecService != null) aecService.initSession(sessionId);
                // 状态切换必须留在读线程：唤醒响应期间要立刻屏蔽 VAD，
                // 晚一步紧随其后的上行音频帧就会被当成用户说话
                chatSession.transitionTo(DeviceState.SPEAKING);
                Thread.startVirtualThread(() -> dialogueService.handleWakeWord(chatSession, message.getText()));
                break;

            default:
                log.warn("未知的listen状态: {}", message.getState());
        }
    }

    private void handleAbortMessage(ChatSession session, AbortMessage message) {
        String deviceId = session.getDevice() != null ? session.getDevice().getDeviceId() : null;
        applicationContext.publishEvent(new ChatAbortedEvent(this, session.getSessionId(), deviceId, message.getReason()));
    }

    private void handleIotMessage(ChatSession chatSession, IotMessage message) {
        String sessionId = chatSession.getSessionId();
        // 处理设备描述信息
        if (message.getDescriptors() != null) {
            log.info("收到IoT设备描述信息 - SessionId: {}: {}", sessionId, message.getDescriptors());
            // 处理设备描述信息的逻辑
            iotService.handleDeviceDescriptors(sessionId, message.getDescriptors());
        }

        // 处理设备状态更新
        if (message.getStates() != null) {
            log.info("收到IoT设备状态更新 - SessionId: {}: {}", sessionId, message.getStates());
            // 处理设备状态更新的逻辑
            iotService.handleDeviceStates(sessionId, message.getStates());
        }
    }

    private void handleGoodbyeMessage(ChatSession session, GoodbyeMessage message) {
        // 检查会话是否已经关闭，避免重复处理
        if (!session.isAudioChannelOpen()) {
            return;
        }

        // 中止正在进行的对话，停止TTS和音频发送。VAD/AEC 由随后的 closeSession 统一清理
        String goodbyeDeviceId = session.getDevice() != null ? session.getDevice().getDeviceId() : null;
        applicationContext.publishEvent(new ChatAbortedEvent(this, session.getSessionId(), goodbyeDeviceId, "设备主动退出"));

        sessionManager.closeSession(session);
    }

    private void handleDeviceMcpMessage(ChatSession chatSession, DeviceMcpMessage message) {
        // 设备可能回来一条没有 payload 或没有 id 的 mcp 消息，取不到请求号就直接忽略
        DeviceMcpPayload payload = message.getPayload();
        Long mcpRequestId = payload == null ? null : payload.getId();
        if (mcpRequestId == null) {
            log.warn("收到缺少请求号的mcp消息 - SessionId: {}", chatSession.getSessionId());
            return;
        }
        // 先摘再完成，避免同一请求被重复应答时二次分发
        CompletableFuture<DeviceMcpMessage> future =
                chatSession.getDeviceMcpHolder().getMcpPendingRequests().remove(mcpRequestId);
        if (future != null) {
            future.complete(message);
        }
    }

    public void handleMessage(Message msg, String sessionId) {
        var chatSession = sessionManager.getSession(sessionId);
        switch (msg) {
            case ListenMessage m -> handleListenMessage(chatSession, m);
            case IotMessage m -> handleIotMessage(chatSession, m);
            case AbortMessage m -> handleAbortMessage(chatSession, m);
            case GoodbyeMessage m -> handleGoodbyeMessage(chatSession, m);
            case DeviceMcpMessage m -> handleDeviceMcpMessage(chatSession, m);
            default -> {
            }
        }
    }
}

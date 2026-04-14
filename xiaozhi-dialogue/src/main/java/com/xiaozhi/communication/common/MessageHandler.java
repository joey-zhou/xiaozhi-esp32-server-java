package com.xiaozhi.communication.common;

import com.xiaozhi.communication.domain.*;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.domain.repository.DeviceRepository;
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
import com.xiaozhi.enums.ListenState;
import com.xiaozhi.event.ChatAbortedEvent;
import com.xiaozhi.role.service.RoleService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);

    @Resource
    private DeviceService deviceService;

    @Resource
    private DeviceRepository deviceRepository;

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

        // 跨实例幽灵会话清理：如果设备之前绑定在其他实例，通知旧实例关闭会话
        // 此时 registerDevice 尚未调用，本实例的 deviceIdToSessionId 无此设备，
        // 所以广播到达本实例时 getSessionByDeviceId 返回 null，不会误关自己
        String previousInstance = deviceRegistry.getInstance(deviceId);
        if (previousInstance != null && !previousInstance.equals(instanceIdHolder.getInstanceId())) {
            logger.info("设备 {} 之前在实例 {} 上，通知旧实例清理幽灵会话", deviceId, previousInstance);
            redisBroadcast.closeDeviceSession(deviceId);
        }

        logger.info("开始查询设备信息 - DeviceId: {}", deviceId);
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
                deviceRepository.updateState(deviceId, newState);
            } catch (Exception e) {
                // 仅记录告警，不关闭会话：状态写库失败不影响设备正常通信
                logger.warn("更新设备在线状态失败 - DeviceId: {}, State: {}", deviceId, newState, e);
            }
        });

    }

    /**
     * 处理连接关闭事件.
     *
     * @param sessionId
     */
    public void afterConnectionClosed(String sessionId) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if (chatSession == null) {
            return;
        }
        // 连接关闭时清理资源
        DeviceBO device = chatSession.getDevice();
        if (device != null) {
            String deviceId = device.getDeviceId();

            // 服务关闭期间跳过状态写库：启动时会 bulk reset 所有设备为离线，无需在关机时逐台写入
            if (!sessionManager.isShuttingDown()) {
                Thread.startVirtualThread(() -> {
                    try {
                        String newState = DeviceBO.DEVICE_STATE_OFFLINE;

                        // 时序保护：检查设备是否已重连
                        ChatSession currentSession = sessionManager.getSessionByDeviceId(deviceId);
                        if (currentSession != null && !sessionId.equals(currentSession.getSessionId())) {
                            return;
                        }

                        deviceRepository.updateState(deviceId, newState);
                        logger.info("连接已关闭 - SessionId: {}, DeviceId: {}, 新状态: {}",
                                sessionId, deviceId, newState);
                    } catch (Exception e) {
                        logger.error("更新设备状态失败", e);
                    }
                });
            }
        }
        // 清理会话
        sessionManager.closeSession(sessionId);
        // 清理VAD会话
        vadService.resetSession(sessionId);
        // 清理AEC会话
        if (aecService != null) aecService.resetSession(sessionId);

    }

    /**
     * 处理音频数据
     *
     * @param sessionId
     * @param opusData
     */
    public void handleBinaryMessage(String sessionId, byte[] opusData) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if ((chatSession == null || !chatSession.isOpen()) && !vadService.isSessionInitialized(sessionId)) {
            return;
        }
        // 委托给DialogueService处理音频数据
        dialogueService.processAudioData(chatSession, opusData);

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
                logger.info("检测到虚拟设备 {}，尝试自动绑定", deviceId);
                
                // 提取用户ID
                String userIdStr = deviceId.substring("user_chat_".length());
                Integer userId = Integer.parseInt(userIdStr);
                
                RoleBO defaultRole = roleService.getDefaultOrFirstBO(userId);
                Integer defaultRoleId = defaultRole != null ? defaultRole.getRoleId() : null;
                
                if (defaultRoleId != null) {
                    // 创建虚拟设备并绑定到默认角色
                    Device createdDevice = Device.newDevice(
                            deviceId, "小助手", "web", userId, defaultRoleId);
                    deviceRepository.save(createdDevice);
                    if (createdDevice.getDeviceId() != null) {
                        logger.info("虚拟设备 {} 自动绑定成功，角色ID: {}", deviceId, defaultRoleId);
                        
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
                                logger.info("虚拟设备 {} 初始化完成，可以开始对话", deviceId);
                            }
                            
                            // 设备已绑定并初始化完成，返回true表示可以继续处理消息
                            return true;
                        }
                    } else {
                        logger.warn("虚拟设备 {} 自动绑定失败", deviceId);
                    }
                } else {
                    logger.warn("用户 {} 没有可用的角色，无法自动绑定虚拟设备", userId);
                }
            } catch (NumberFormatException e) {
                logger.error("解析虚拟设备ID失败: {}", deviceId, e);
            } catch (Exception e) {
                logger.error("自动绑定虚拟设备失败: {}", deviceId, e);
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
                Path audioPath;
                if (!StringUtils.hasText(codeResult.getAudioPath())) {
                    String codeMessage = "请到设备管理页面添加设备，输入验证码" + codeResult.getCode();
                    audioPath = ttsFactory.getDefaultTtsService().textToSpeech(codeMessage);
                    deviceService.updateCodeAudioPath(deviceId, sessionId, codeResult.getCode(), audioPath.toString());
                } else {
                    audioPath = Path.of(codeResult.getAudioPath());
                }

                player.play(codeResult.getCode(), audioPath);
                // 延迟一段时间后再解除标记
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                captchaGenerationInProgress.remove(deviceId);

            } catch (Exception e) {
                logger.error("处理未绑定设备失败", e);
                captchaGenerationInProgress.remove(deviceId);
            }
        });
        
        // 返回false表示需要验证码流程，不继续处理当前消息
        return false;
    }

    private void handleListenMessage(ChatSession chatSession, ListenMessage message) {
        String sessionId = chatSession.getSessionId();
        logger.info("收到listen消息 - SessionId: {}, State: {}, Mode: {}", sessionId, message.getState(), message.getMode());

        // 如果会话标记为即将关闭，忽略listen消息
        if (chatSession.getPlayer().getFunctionAfterChat()!= null) {
            return;
        }

        chatSession.setMode(message.getMode());

        // 根据state处理不同的监听状态
        switch (message.getState()) {
            case ListenState.Start:
                // 设备开始录音，进入聆听状态
                logger.info("开始监听 - Mode: {}", message.getMode());

                chatSession.transitionTo(DeviceState.LISTENING);

                // 初始化VAD会话
                vadService.initSession(sessionId);
                // 初始化AEC会话
                if (aecService != null) aecService.initSession(sessionId);
                break;

            case ListenState.Stop:
                // 停止监听
                logger.info("停止监听");

                // 关闭音频流，恢复到 IDLE
                chatSession.completeAudioStream();
                chatSession.closeAudioStream();
                chatSession.transitionTo(DeviceState.IDLE);
                // 重置VAD会话
                vadService.resetSession(sessionId);
                // 注意：不重置 AEC 会话，保留已收敛的滤波器状态供后续对话复用
                break;

            case ListenState.Text:
                // 检测聊天文本输入 — 确保 AEC 在 TTS 开始前已初始化
                if (aecService != null) aecService.initSession(sessionId);
                Player player = chatSession.getPlayer();
                if (player != null ) {
                    String modeValue = message.getMode() != null ? message.getMode().getValue() : null;
                    String abortDeviceId = chatSession.getDevice() != null ? chatSession.getDevice().getDeviceId() : null;
                    applicationContext.publishEvent(new ChatAbortedEvent(this, chatSession.getSessionId(), abortDeviceId, modeValue));
                }
                // 确保 Persona 存在、通知设备、更新活跃时间
                sessionManager.updateLastActivity(sessionId);
                personaFactory.buildPersona(chatSession);
                messageService.sendSttMessage(chatSession, message.getText());
                logger.info("处理聊天文字输入: \"{}\"", message.getText());
                dialogueService.handleText(chatSession, SttResult.textOnly(message.getText()));
                break;

            case ListenState.Detect:
                // 检测到唤醒词 — 确保 AEC 在 TTS 开始前已初始化
                if (aecService != null) aecService.initSession(sessionId);
                dialogueService.handleWakeWord(chatSession, message.getText());
                break;

            default:
                logger.warn("未知的listen状态: {}", message.getState());
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
            logger.info("收到IoT设备描述信息 - SessionId: {}: {}", sessionId, message.getDescriptors());
            // 处理设备描述信息的逻辑
            iotService.handleDeviceDescriptors(sessionId, message.getDescriptors());
        }

        // 处理设备状态更新
        if (message.getStates() != null) {
            logger.info("收到IoT设备状态更新 - SessionId: {}: {}", sessionId, message.getStates());
            // 处理设备状态更新的逻辑
            iotService.handleDeviceStates(sessionId, message.getStates());
        }
    }

    private void handleGoodbyeMessage(ChatSession session, GoodbyeMessage message) {
        // 检查会话是否已经关闭，避免重复处理
        if (!session.isAudioChannelOpen()) {
            return;
        }

        // 先清理VAD和AEC会话，防止后续的listen消息重新初始化
        String sessionId = session.getSessionId();
        vadService.resetSession(sessionId);
        if (aecService != null) aecService.resetSession(sessionId);

        // 中止正在进行的对话，停止TTS和音频发送
        String goodbyeDeviceId = session.getDevice() != null ? session.getDevice().getDeviceId() : null;
        applicationContext.publishEvent(new ChatAbortedEvent(this, session.getSessionId(), goodbyeDeviceId, "设备主动退出"));

        sessionManager.closeSession(session);
    }

    private void handleDeviceMcpMessage(ChatSession chatSession, DeviceMcpMessage message) {
        Long mcpRequestId = message.getPayload().getId();
        CompletableFuture<DeviceMcpMessage> future = chatSession.getDeviceMcpHolder().getMcpPendingRequests().get(mcpRequestId);
        if(future != null){
            future.complete(message);
            chatSession.getDeviceMcpHolder().getMcpPendingRequests().remove(mcpRequestId);
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

package com.xiaozhi.communication.common;

import com.xiaozhi.communication.domain.*;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.aec.AecService;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.service.*;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.enums.ListenState;
import com.xiaozhi.event.ChatAbortEvent;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.service.SysRoleService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);

    @Resource
    private SysDeviceService deviceService;

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
    private ChatService chatService;

    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private ToolsGlobalRegistry toolsGlobalRegistry;

    @Resource
    private SysRoleService roleService;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private MessageService messageService;

    @Resource
    private SysMessageService sysMessageService;

    @Autowired(required = false)
    private AecService aecService;

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

        logger.info("开始查询设备信息 - DeviceId: {}", deviceId);
        SysDevice device = Optional.ofNullable(deviceService.selectDeviceById(deviceId)).orElse(new SysDevice());
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
    private void initializeBoundDevice(ChatSession chatSession, SysDevice device) {
        String deviceId = device.getDeviceId();
        String sessionId = chatSession.getSessionId();
        
        //这里需要放在虚拟线程外
        ToolsSessionHolder toolsSessionHolder = new ToolsSessionHolder(chatSession.getSessionId(),
                device, toolsGlobalRegistry);
        chatSession.setFunctionSessionHolder(toolsSessionHolder);
        // 从数据库获取角色描述。device.getRoleId()表示当前设备的当前活跃角色，或者上次退出时的活跃角色。
        SysRole role = roleService.selectRoleById(device.getRoleId());

        chatService.buildPersona(chatSession, device, role);

        // 连接建立时就初始化 AEC，确保后续任何 TTS 播放（含唤醒响应）的参考帧都不会被丢弃
        if (aecService != null) aecService.initSession(sessionId);

        //以上同步处理结束后，再启动虚拟线程进行设备初始化，确保chatSession中已设置的sysDevice信息 TODO 性能优化后续再做

        try {
            // 更新设备状态
            deviceService.update(new SysDevice()
                    .setDeviceId(device.getDeviceId())
                    .setState(chatSession instanceof WebSocketSession ? SysDevice.DEVICE_STATE_ONLINE : SysDevice.DEVICE_STATE_STANDBY));

        } catch (Exception e) {
            logger.error("设备初始化失败 - DeviceId: " + deviceId, e);
            try {
                sessionManager.closeSession(sessionId);
            } catch (Exception ex) {
                logger.error("关闭WebSocket连接失败", ex);
            }
        }

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
        SysDevice device = sessionManager.getDeviceConfig(sessionId);
        if (device != null) {
            String deviceId = device.getDeviceId();

            // 服务关闭期间跳过状态写库：启动时会 bulk reset 所有设备为离线，无需在关机时逐台写入
            if (!sessionManager.isShuttingDown()) {
                Thread.startVirtualThread(() -> {
                    try {
                        // 根据连接类型和断开原因判断设备状态：
                        // WebSocket 连接关闭 -> OFFLINE（离线）
                        String newState = SysDevice.DEVICE_STATE_OFFLINE;

                        // 时序保护：检查设备是否已重连
                        ChatSession currentSession = sessionManager.getSessionByDeviceId(deviceId);
                        if (currentSession != null && !sessionId.equals(currentSession.getSessionId())) {
                            return;
                        }

                        deviceService.update(new SysDevice()
                                .setDeviceId(deviceId)
                                .setState(newState));
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
    public boolean handleUnboundDevice(String sessionId, SysDevice device) {
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
                
                // 查询用户的默认角色
                SysRole queryRole = new SysRole();
                queryRole.setUserId(userId);
                List<SysRole> roles = roleService.query(queryRole, null);
                
                Integer defaultRoleId = null;
                // 查询用户所有角色，只查一次，然后遍历查找默认的，没有默认的取第一个
                if (roles != null && !roles.isEmpty()) {
                    for (SysRole role : roles) {
                        if ("1".equals(role.getIsDefault())) {
                            defaultRoleId = role.getRoleId();
                            break;
                        }
                    }
                    if (defaultRoleId == null) {
                        defaultRoleId = roles.get(0).getRoleId();
                    }
                }
                
                if (defaultRoleId != null) {
                    // 创建虚拟设备并绑定到默认角色
                    SysDevice virtualDevice = new SysDevice();
                    virtualDevice.setDeviceId(deviceId);
                    virtualDevice.setDeviceName("小助手");
                    virtualDevice.setUserId(userId);
                    virtualDevice.setType("web");
                    virtualDevice.setState(SysDevice.DEVICE_STATE_ONLINE);
                    virtualDevice.setRoleId(defaultRoleId);
                    
                    // 添加设备
                    int result = deviceService.add(virtualDevice);
                    
                    if (result > 0) {
                        logger.info("虚拟设备 {} 自动绑定成功，角色ID: {}", deviceId, defaultRoleId);
                        
                        // 重新查询设备信息
                        SysDevice boundDevice = deviceService.selectDeviceById(deviceId);
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
                Player player = new ScheduledPlayer(chatSession, messageService, sysMessageService, aecService);
                // 设备已注册但未配置模型
                if (device.getDeviceName() != null && device.getRoleId() == null) {
                    String message = "设备未配置角色，请到角色配置页面完成配置后开始对话";

                    String audioFilePath = ttsFactory.getDefaultTtsService().textToSpeech(message);

                    player.play(message, Path.of(audioFilePath));

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
                SysDevice codeResult = deviceService.generateCode(device);
                String audioFilePath;
                if (!StringUtils.hasText(codeResult.getAudioPath())) {
                    String codeMessage = "请到设备管理页面添加设备，输入验证码" + codeResult.getCode();
                    audioFilePath = ttsFactory.getDefaultTtsService().textToSpeech(codeMessage);
                    codeResult.setDeviceId(deviceId);
                    codeResult.setSessionId(sessionId);
                    codeResult.setAudioPath(audioFilePath);
                    deviceService.updateCode(codeResult);
                } else {
                    audioFilePath = codeResult.getAudioPath();
                }

                player.play(codeResult.getCode(), Path.of(audioFilePath));
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

        boolean isGoodbye = false;

        // 检查会话是否已发送goodbye，如果是则忽略listen消息
        if (isGoodbye) {
            logger.info("goodbye中，忽略listen消息{} - SessionId: {}", message.toString(), sessionId);
            return;
        }

        // 如果会话标记为即将关闭，忽略listen消息
        if (chatSession.getPlayer().getFunctionAfterChat()!= null) {
            return;
        }

        chatSession.setMode(message.getMode());

        // 根据state处理不同的监听状态
        switch (message.getState()) {
            case ListenState.Start:
                // 开始监听，准备接收音频数据
                logger.info("开始监听 - Mode: {}", message.getMode());

                // 如果处于唤醒响应状态，先发送tts start，让设备进入播放状态
                if (chatSession.isInWakeupResponse()) {
                    messageService.sendTtsMessage(chatSession, null, "start");
                }

                // 初始化VAD会话
                vadService.initSession(sessionId);
                // 初始化AEC会话
                if (aecService != null) aecService.initSession(sessionId);
                break;

            case ListenState.Stop:
                // 停止监听
                logger.info("停止监听");

                // 关闭音频流
                sessionManager.completeAudioStream(sessionId);
                sessionManager.closeAudioStream(sessionId);
                sessionManager.setStreamingState(sessionId, false);
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
                    applicationContext.publishEvent(new ChatAbortEvent(chatSession, modeValue));
                }
                dialogueService.handleText(chatSession, message.getText());
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
        applicationContext.publishEvent(new ChatAbortEvent(session, message.getReason()));
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

        // 先清理VAD和AEC会话，防止后续的listen消息重新初始化
        String sessionId = session.getSessionId();
        vadService.resetSession(sessionId);
        if (aecService != null) aecService.resetSession(sessionId);

        // 中止正在进行的对话，停止TTS和音频发送
        applicationContext.publishEvent(new ChatAbortEvent(session, "设备主动退出"));

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

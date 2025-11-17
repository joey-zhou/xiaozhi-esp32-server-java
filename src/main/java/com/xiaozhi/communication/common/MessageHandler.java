package com.xiaozhi.communication.common;

import com.xiaozhi.communication.domain.*;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.llm.memory.ConversationFactory;
import com.xiaozhi.dialogue.llm.providers.OpenAiLlmService;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.service.AudioService;
import com.xiaozhi.dialogue.service.DialogueService;
import com.xiaozhi.dialogue.service.IotService;
import com.xiaozhi.dialogue.service.VadService;
import com.xiaozhi.dialogue.stt.factory.SttServiceFactory;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.enums.ListenState;
import com.xiaozhi.event.ChatAbortEvent;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysRoleService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
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
    private AudioService audioService;

    @Resource
    private VadService vadService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private SysConfigService configService;

    @Resource
    private DialogueService dialogueService;

    @Resource
    private IotService iotService;

    @Resource
    private TtsServiceFactory ttsFactory;

    @Resource
    private SttServiceFactory sttFactory;

    @Resource
    private ConversationFactory conversationFactory;

    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private ToolsGlobalRegistry toolsGlobalRegistry;

    @Resource
    private SysRoleService roleService;

    @Resource
    private ApplicationContext applicationContext;

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
        Conversation conversation = conversationFactory.initConversation(device, role, sessionId);
        chatSession.setConversation(conversation);

        //以上同步处理结束后，再启动虚拟线程进行设备初始化，确保chatSession中已设置的sysDevice信息
        Thread.startVirtualThread(() -> {
            try {
                if (role.getSttId() != null) {
                    SysConfig sttConfig = configService.selectConfigById(role.getSttId());
                    if (sttConfig != null) {
                        sttFactory.getSttService(sttConfig);// 提前初始化，加速后续使用
                    }
                }
                if (role.getTtsId() != null) {
                    SysConfig ttsConfig = configService.selectConfigById(role.getTtsId());
                    if (ttsConfig != null) {
                        ttsFactory.getTtsService(ttsConfig, role.getVoiceName(), role.getTtsPitch(), role.getTtsSpeed());// 提前初始化，加速后续使用
                    }
                }
                if (role.getModelId() != null) {
                    ChatModel chatModel = chatModelFactory.takeChatModel(chatSession);// 提前初始化，加速后续使用
                    if(chatModel instanceof OpenAiChatModel){
                        Thread.startVirtualThread(()->{
                            //如果是openApi类型的ai，异步校验当前模型是否支持function call
                            // 根据配置ID查询配置
                            SysConfig config = configService.selectConfigById(role.getModelId());
                            String model = config.getConfigName();
                            String endpoint = config.getApiUrl();
                            String apiKey = config.getApiKey();
                            OpenAiLlmService openAiLlmService = new OpenAiLlmService(endpoint, apiKey, model);
                            chatSession.setSupportFunctionCall(openAiLlmService.testFunctionCall());
                        });
                    }
                }

                // 更新设备状态
                deviceService.update(new SysDevice()
                        .setDeviceId(device.getDeviceId())
                        .setState(chatSession instanceof WebSocketSession ? SysDevice.DEVICE_STATE_ONLINE : SysDevice.DEVICE_STATE_STANDBY)
                        .setLastLogin(new Date().toString()));

            } catch (Exception e) {
                logger.error("设备初始化失败 - DeviceId: " + deviceId, e);
                try {
                    sessionManager.closeSession(sessionId);
                } catch (Exception ex) {
                    logger.error("关闭WebSocket连接失败", ex);
                }
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
        if (chatSession == null || !chatSession.isOpen()) {
            return;
        }
        // 连接关闭时清理资源
        SysDevice device = sessionManager.getDeviceConfig(sessionId);
        if (device != null) {
            Thread.startVirtualThread(() -> {
                try {
                    deviceService.update(new SysDevice()
                            .setDeviceId(device.getDeviceId())
                            .setState(SysDevice.DEVICE_STATE_OFFLINE)
                            .setLastLogin(new Date().toString()));
                    logger.info("连接已关闭 - SessionId: {}, DeviceId: {}", sessionId, device.getDeviceId());
                } catch (Exception e) {
                    logger.error("更新设备状态失败", e);
                }
            });
        }
        // 清理会话
        sessionManager.closeSession(sessionId);
        // 清理VAD会话
        vadService.resetSession(sessionId);
        // 清理音频处理会话
        audioService.cleanupSession(sessionId);
        // 清理对话
        dialogueService.cleanupSession(sessionId);

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
                // 设备已注册但未配置模型
                if (device.getDeviceName() != null && device.getRoleId() == null) {
                    String message = "设备未配置角色，请到角色配置页面完成配置后开始对话";

                    String audioFilePath = ttsFactory.getDefaultTtsService().textToSpeech(message);
                    audioService.sendAudioMessage(chatSession, new DialogueService.Sentence(message, audioFilePath), true,
                            true);

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

                audioService.sendAudioMessage(chatSession,
                        new DialogueService.Sentence(codeResult.getCode(), codeResult.getAudioPath()), true, true);

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
        chatSession.setMode(message.getMode());

        // 根据state处理不同的监听状态
        switch (message.getState()) {
            case ListenState.Start:
                // 开始监听，准备接收音频数据
                logger.info("开始监听 - Mode: {}", message.getMode());

                // 初始化VAD会话
                vadService.initSession(sessionId);
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
                break;

            case ListenState.Text:
                // 检测聊天文本输入
                if (audioService.isPlaying(sessionId)) {
                    applicationContext.publishEvent(new ChatAbortEvent(chatSession, message.getMode().getValue()));
                }
                dialogueService.handleText(chatSession, message.getText(), null);
                break;

            case ListenState.Detect:
                // 检测到唤醒词
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
        sessionManager.closeSession(session);
        if(!(session instanceof WebSocketSession)){
            if (session.getSysDevice() != null) {
                Thread.startVirtualThread(() -> {
                    try {
                        deviceService.update(new SysDevice()
                                .setDeviceId(session.getSysDevice().getDeviceId())
                                .setState(SysDevice.DEVICE_STATE_STANDBY)
                                .setLastLogin(new Date().toString()));
                        logger.info("设备连接进入待机状态 - SessionId: {}, DeviceId: {}", session.getSessionId(), session.getSysDevice().getDeviceId());
                    } catch (Exception e) {
                        logger.error("更新设备状态失败", e);
                    }
                });
            }
        }
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

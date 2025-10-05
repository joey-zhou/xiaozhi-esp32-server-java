package com.xiaozhi.communication.common;

import com.xiaozhi.communication.domain.*;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.llm.memory.ConversationFactory;
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
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysRoleService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
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
    private TtsServiceFactory ttsService;

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

    @Autowired
    private ConversationFactory conversationFactory;

    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private ToolsGlobalRegistry toolsGlobalRegistry;

    @Resource
    private SysRoleService roleService;

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
                            ttsFactory.getTtsService(ttsConfig, role.getVoiceName());// 提前初始化，加速后续使用
                        }
                    }
                    if (role.getModelId() != null) {
                        chatModelFactory.takeChatModel(chatSession);// 提前初始化，加速后续使用
                        // 注册全局函数
                        toolsSessionHolder.registerGlobalFunctionTools(chatSession);
                    }

                    // 更新设备状态
                    deviceService.update(new SysDevice()
                            .setDeviceId(deviceId)
                            .setState(SysDevice.DEVICE_STATE_ONLINE)
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
                    logger.info("WebSocket连接关闭 - SessionId: {}, DeviceId: {}", sessionId, device.getDeviceId());
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
        // 清理Conversation缓存的对话历史。
        Conversation conversation = chatSession.getConversation();
        if (conversation != null) {
            conversation.clear();
        }
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

    public void handleUnboundDevice(String sessionId, SysDevice device) {
        String deviceId;
        if (device == null || device.getDeviceId() == null) {
            logger.error("设备或设备ID为空，无法处理未绑定设备");
            return;
        }
        deviceId = device.getDeviceId();
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if (chatSession == null || !chatSession.isOpen()) {
            return;
        }
        // 检查是否已经在处理中，使用CAS操作保证线程安全
        Boolean previous = captchaGenerationInProgress.putIfAbsent(deviceId, true);
        if (previous != null && previous) {
            return; // 已经在处理中
        }

        Thread.startVirtualThread(() -> {
            try {
                // 设备已注册但未配置模型
                if (device.getDeviceName() != null && device.getRoleId() == null) {
                    String message = "设备未配置角色，请到角色配置页面完成配置后开始对话";

                    String audioFilePath = ttsService.getDefaultTtsService().textToSpeech(message);
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
                    audioFilePath = ttsService.getDefaultTtsService().textToSpeech(codeMessage);
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
                    dialogueService.abortDialogue(chatSession, message.getMode().getValue());
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
        dialogueService.abortDialogue(session, message.getReason());
    }

    private void handleIotMessage(ChatSession chatSession, IotMessage message) {
        String sessionId = chatSession.getSessionId();
        logger.info("收到IoT消息 - SessionId: {}", sessionId);

        // 处理设备描述信息
        if (message.getDescriptors() != null) {
            logger.info("收到设备描述信息: {}", message.getDescriptors());
            // 处理设备描述信息的逻辑
            iotService.handleDeviceDescriptors(sessionId, message.getDescriptors());
        }

        // 处理设备状态更新
        if (message.getStates() != null) {
            logger.info("收到设备状态更新: {}", message.getStates());
            // 处理设备状态更新的逻辑
            iotService.handleDeviceStates(sessionId, message.getStates());
        }
    }

    private void handleGoodbyeMessage(ChatSession session, GoodbyeMessage message) {
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

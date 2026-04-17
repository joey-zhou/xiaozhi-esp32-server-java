package com.xiaozhi.dialogue.llm.factory;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.ConversationFactory;
import com.xiaozhi.dialogue.audio.AecService;
import com.xiaozhi.dialogue.playback.OpusRecorder;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.ScheduledPlayer;
import com.xiaozhi.dialogue.playback.Synthesizer;
import com.xiaozhi.dialogue.playback.SynthesizerFactory;
import com.xiaozhi.dialogue.runtime.GoodbyeMessageSupplier;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.ai.stt.SttServiceFactory;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.ai.tool.ToolRegistrationService;
import com.xiaozhi.dialogue.adapter.ChatSessionToolAdapter;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.dialogue.llm.handler.DialogueListener;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.Assert;
import org.springframework.stereotype.Component;



/**
 * Persona 工厂类，负责构建完整的 Persona 实例（含 STT/TTS/LLM/Player 等组件）。
 */
@Component
public class PersonaFactory {
    private static final Logger logger = LoggerFactory.getLogger(PersonaFactory.class);


    @Resource
    private MessageService chatMessageService;
    @Resource
    private ConfigService configService;
    @Resource
    private ChatModelFactory chatModelFactory;
    @Resource
    private ToolRegistrationService toolRegistrationService;
    @Resource
    private TtsServiceFactory ttsFactory;
    @Resource
    private SttServiceFactory sttFactory;
    @Resource
    private ConversationFactory conversationFactory;
    @Resource
    private RoleService roleService;
    @Resource
    private MessageSender sessionMessageService;
    @Resource
    private SessionManager sessionManager;
    @Resource
    private AecService aecService;

    @Resource
    private GoodbyeMessageSupplier goodbyeMessages;
    @Resource
    private DialogueListener dialogueListener;
    @Resource
    private StorageServiceFactory storageServiceFactory;

    /**
     * 构建完整的 Persona 实例。
     * ToolCallbacks 当前通过 session.getToolCallbacks() 动态获取，支持MCP/IoT工具运行时注册。
     * Player 不完全属于 Persona，在角色不存在时 Player 就应先于 Persona 构建，以应对错误信息播报。
     *
     * @param session 当前会话
     * @param device 设备信息
     * @param role 角色配置，当与当前Persona不同时才需要构建新的Persona
     * @return 构建好的 Persona 实例
     */
    public Persona buildPersona(ChatSession session, DeviceBO device, RoleBO role) {
        Assert.notNull(device, "device cannot be null");
        Assert.notNull(role, "role cannot be null");

        // 幂等保护：Persona 已存在则跳过重建
        if (session.getPersona() != null) {
            return session.getPersona();
        }

        // Player应该是可以独立于Persona而存在的，同时也可以看作是角色的嘴巴/声带。
        Player player = session.getPlayer();
        if(player == null){
            player = new ScheduledPlayer(session, sessionMessageService);
            player.setOpusRecorder(new OpusRecorder(session, chatMessageService, aecService, storageServiceFactory));
            session.setPlayer(player);
        }
        // 初始化Conversation(相当于角色的记忆）
        String ownerId = device.getDeviceId();
        Integer userId = device.getUserId();
        Conversation conversation = conversationFactory.initConversation(ownerId, userId, role, session.getSessionId());

        // 获取STT服务
        SttService sttService = initSttService(role);

        // 初始化语音合成器
        Synthesizer synthesizer = initSynthesizer(session,player,role);

        //处理工具注册（系统工具 + 设备MCP）
        toolRegistrationService.register(new ChatSessionToolAdapter(session));

        // 获取ChatModel
        ChatModel chatModel = chatModelFactory.getChatModel(role);

        // MCP/IoT 工具已注册完毕，获取完整的工具列表传给 Persona
        var toolCallbacks = session.getToolCallbacks();

        Persona persona = Persona.builder()
                .sessionManager(sessionManager)
                .sessionId(session.getSessionId())
                .conversation(conversation)
                .sttService(sttService)
                .chatModel(chatModel)
                .synthesizer(synthesizer)
                .player(session.getPlayer())
                .toolCallbacks(toolCallbacks)
                .listener(dialogueListener)
                .goodbyeMessages(goodbyeMessages)
                .build();
        session.setPersona(persona);
        return persona;
    }

    /**
     * 重载：仅传 session，自动从 session 获取 device，从 DB/缓存获取 role。
     */
    public Persona buildPersona(ChatSession session) {
        if (session.getPersona() != null) {
            return session.getPersona();
        }
        DeviceBO device = session.getDevice();
        RoleBO role = roleService.getBO(device.getRoleId());
        return buildPersona(session, device, role);
    }

    /**
     * 初始化STT服务，将重要信息记录日志
     * @param role
     * @return
     */
    private SttService initSttService(RoleBO role){
        Assert.notNull(role, "role cannot be null");
        var sttId = role.getSttId();
        if (sttId == null || sttId <= 0) {
            logger.warn("角色没有配置STT服务 - Role: {},默认使用vosk", role.getRoleName());
            return sttFactory.getSttService(null);
        }
        var sttConfig = configService.getBO(sttId);
        if(sttConfig == null){
            logger.error("无法获取STT服务配置 - Id: {}", sttId);
            return null;
        }
        SttService sttService = sttFactory.getSttService(sttConfig);
        if (sttService == null) {
            logger.error("无法获取STT服务 - Provider: {}", sttConfig != null ? sttConfig.getProvider() : "null");
        }
        return sttService;
    }

    /**
     * 初始化对话状态
     */
    public Synthesizer initSynthesizer(ChatSession session, Player player, RoleBO role) {
        // 新增加的设备很有可能没有配置TTS，采用默认Edge需要传递null
        ConfigBO ttsConfig = null;
        if (role.getTtsId() != null && role.getTtsId() > 0) {
            ttsConfig = configService.getBO(role.getTtsId());
        }
        String voiceName = role.getVoiceName();
        TtsService ttsService = ttsFactory.getTtsService(ttsConfig, voiceName, role.getTtsPitch(), role.getTtsSpeed());

        return SynthesizerFactory.create(session, ttsService, player);

    }

}

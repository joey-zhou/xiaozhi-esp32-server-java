package com.xiaozhi.dialogue.llm;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.dialogue.llm.memory.*;
import com.xiaozhi.dialogue.aec.AecService;
import com.xiaozhi.dialogue.service.*;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.dialogue.stt.factory.SttServiceFactory;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.*;
import com.xiaozhi.event.ChatSessionCloseEvent;
import com.xiaozhi.mcp.McpSessionManager;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.service.SysRoleService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.util.Assert;
import org.springframework.stereotype.Service;

import java.util.*;


/**
 *
 * 负责管理和协调LLM相关功能
 * 未来考虑：改成
 * 未来考虑：在连接通过认证，可正常对话时，创建实例，构建好一个完整的Persona。
 * ChatService 最终变为Persona的工厂类
 */
@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    public static final String TOOL_CONTEXT_SESSION_KEY = "session";

    @Autowired
    private SysMessageService sysMessageService;
    @Resource
    private SysConfigService configService;
    @Resource
    private ChatModelFactory chatModelFactory;
    @Resource
    private McpSessionManager mcpSessionManager;
    @Resource
    private TtsServiceFactory ttsFactory;
    @Resource
    private SttServiceFactory sttFactory;
    @Resource
    private ConversationFactory conversationFactory;
    @Resource
    private SysRoleService roleService;
    @Resource
    private MessageService messageService;
    @Resource
    private SessionManager sessionManager;

    @Autowired(required = false)
    private AecService aecService;

    @Resource
    private GoodbyeMessageSupplier goodbyeMessages;

    /**
     * TODO 最终要将ChatService变为Persona工厂类。
     * 目前只有 终端设备建立连接与唤醒时需要 初始化 Persona。
     * TODO 大多数时候， ToolCallbacks 是不会在某次对话时发生变化的，是可以在构建Persona时构建一次ToolCallbacks 。
     * 建议将外部的mcp 的 ToolCallbacks 挂在 Prompt 的ChatOptins ，而本地固定的工具，则在构建时挂在 ChatModel的ChatOptions。
     * 原本的 ChatModelFactory的所有信息都是从ChatSession里面关联获取，只是为了传参方便，但不是一个好的实现方式。
     * TODO 未来应该改为传递 与ChatSession不相关的配置对象或实体对象。更应该使用 config, role。
     *
     * Player不完全属于Persona，在角色还不存在的时候，只要Device与连接成功建立，Player就应该先于Persona构建，以应对一些可能存在的需要通过扬声器播报的错误信息。
     * 复杂一些的情况，也可以构建一个系统级的特殊的Persona，用于播放错误信息。TODO 待思考可行性。
     *
     * 这个方法不考虑 多角色的情况。将多角色作为一种特殊情况在另一个方法里考虑。
     * @param session
     * @param role 角色可以是在唤醒时由终端设备上传，应该在外面做了初判SysRole资格，当与当前Persona不同时才需要构建新的Persona。
     * @return
     */
    public Persona buildPersona(ChatSession session, SysDevice device, SysRole role) {
        Assert.notNull(device, "device cannot be null");
        Assert.notNull(role, "role cannot be null");

        // Player应该是可以独立于Persona而存在的，同时也可以看作是角色的嘴巴/声带。
        Player player = session.getPlayer();
        if(player == null){
            player = new ScheduledPlayer(session, messageService, sysMessageService, aecService);
            session.setPlayer(player);
        }
        // 初始化Conversation(相当于角色的记忆）
        Conversation conversation = conversationFactory.initConversation(device, role, session.getSessionId());

        // 获取STT服务
        SttService sttService = initSttService(role);

        // 初始化语音合成器
        Synthesizer synthesizer = initSynthesizer(session,player,role);

        //处理mcp自定义
        mcpSessionManager.customMcpHandler(session);

        // 获取ChatModel
        ChatModel chatModel = chatModelFactory.takeChatModel(session);

        // TODO 后续重构将串联 speechFlux与播放的职能纳入到 Persona
        Persona persona = Persona.builder()
                .session(session)
                .conversation(conversation)
                .sttService(sttService)
                .chatModel(chatModel)
                .synthesizer(synthesizer)
                .player(session.getPlayer())
                .messageService(sysMessageService)
                .goodbyeMessages(goodbyeMessages)
                .build();
        session.setPersona(persona);
        return persona;
    }

    /**
     * 初始化STT服务，将重要信息记录日志
     * @param role
     * @return
     */
    private SttService initSttService(SysRole role){
        Assert.notNull(role, "role cannot be null");
        var sttId = role.getSttId();
        if (sttId == null) {
            logger.warn("角色没有配置STT服务 - Role: {},默认使用vosk", role.getRoleName());
            return sttFactory.getSttService(null);
        }
        var sttConfig = configService.selectConfigById(sttId);
        if(sttConfig == null){
            logger.error("无法获取STT服务配置 - Id: {}", sttId);
            return null;
        }
        SttService sttService = sttFactory.getSttService(sttConfig);
        if (sttService == null) {
            logger.error("无法获取STT服务 - Provider: {}", sttConfig != null ? sttConfig.getProvider() : "null");
        }else{
            logger.info("角色'{}'使用STT服务 - Provider: {}, Service: {}", sttConfig.getProvider(), sttService.getClass().getSimpleName());
        }
        return sttService;
    }

    /**
     * 初始化对话状态
     * ChatSession chatSession
     */
    public Synthesizer initSynthesizer(ChatSession session,Player player,SysRole role) {
        // 新增加的设备很有可能没有配置TTS，采用默认Edge需要传递null
        SysConfig ttsConfig;
        if (role.getTtsId() != null) {
            ttsConfig = configService.selectConfigById(role.getTtsId());
        } else {
            ttsConfig = null;
        }
        String voiceName = role.getVoiceName();
        TtsService ttsService = ttsFactory.getTtsService(ttsConfig, voiceName, role.getTtsPitch(), role.getTtsSpeed());

        return new FileSynthesizer(session, ttsService, player);

    }

    /**
     * TODO 资源的清理，可以考虑实现为 Listener的模式， 监听会话关闭事件。
     *  ChatSessionCloseEvent可能不适合非容器管理的对象。
     * 清理Conversation缓存的对话历史。
     * @param event
     */
    @EventListener
    public void handleSessionClose(ChatSessionCloseEvent event) {
        Optional.ofNullable(event.getSession())
                .map(ChatSession::getPersona)
                .map(Persona::getConversation)
                .ifPresent(Conversation::clear);
        // 清理ChatSession中可能绑定的播放器资源
        Optional.ofNullable(event.getSession())
                .map(ChatSession::getPlayer)
                .ifPresent(Player::stop);

    }
}

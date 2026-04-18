package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.ConversationContext;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.Synthesizer;
import com.xiaozhi.ai.stt.SttService;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
/**
 * 人物角色、虚拟形象，描述角色的属性和行为。Domain Entity: CharacterRole(聊天角色,Persona)，管理对话历史记录，管理对话工具调用等。
 * 聚合着ChatModel、TTS(Synthersizer)、Player
 *
 * Persona 与 ChatSession 主要有两个关联：
 * 一是收到消息时，需要从 ChatSession 传导给到 Persona，然后 Persona 将消息传递给 ChatModel。
 * 二是发送消息时，需要从 Persona 将消息传递给 ChatSession。
 *
 * 用户音频文件 Path 通过 ChatSession.getUserAudioPath() 关联到 DialogueTurn，
 * DialogueTurn 作为 chatStream() 方法内局部变量构建（已实现）。
 *
 * 生命周期不同时间节点的几个事件：
 * 1. 接收到 UserSpeech, 获得完整语音时;
 * 2. ASR 识别出 UserText, 已进行 STT 语音识别后，获得了文本时。
 * 3. LLM 响应 AssistantText, 已进行 LLM 生成消息后，获得了 PromptTokens 时。
 * 4. TTS 合成 AssistantSpeech,
 * 5. Player 播放完语音。
 *
 * Persona 和 Conversation 都属于 Domain，不属于 Infrastructure，不考虑持久化存储。
 * 持久化由 PersonaListener.onDialogueTurn(DialogueTurn) 回调处理。
 * ConversationIdentifier = deviceId + sessionId + roleId
 */
@Slf4j
@Builder(toBuilder = true)
public class Persona {

    /**
     * ToolContext 中传递 sessionId 而非整个 ChatSession，避免序列化问题。
     * XiaoZhiToolCallingManager 负责通过 SessionManager 还原为 ChatSession 再传给 Function。
     */
    public static final String TOOL_CONTEXT_SESSION_ID_KEY = "sessionId";

    private final SessionManager sessionManager;
    
    @Setter
    private String sessionId;

    private PersonaListener listener;

    @Getter
    private SttService sttService;

    /**
     * 与LLM Provider通信的具体实现类
     */
    private ChatModel chatModel;
    private GoodbyeMessageSupplier goodbyeMessages;

    @Getter
    private Synthesizer synthesizer;

    @Getter
    private Player player;

    /**
     * 一个Session在某个时刻，只有一个活跃的Conversation。
     * 当切换角色时，Conversation应该释放新建。切换角色一般是不频繁的。
     */
    @Getter
    private Conversation conversation;

    /**
     * 工具回调列表，由 PersonaFactory 构建时从 DialogueContext 传入。
     * chatStream() 从此字段获取工具列表，使 Persona 不再依赖 session.getToolCallbacks()。
     */
    @Builder.Default
    private List<ToolCallback> toolCallbacks = new ArrayList<>();


    // PersonaListener 回调实现了核心与辅助的分离：Persona 只通知"发生了什么"，持久化和监控由外部实现。

    /**
     * 获取ChatSession
     */
    private ChatSession getSession() {
        return sessionManager.getSession(sessionId);
    }

    /**
     * 处理用户查询（流式方式）
     * @param userMessage         用户消息
     * @param useFunctionCall 是否使用函数调用
     */
    private Flux<ChatResponse> chatStream(Instant now, UserMessage userMessage, boolean useFunctionCall) {
        // userSpeechPath 从 session 中获取，避免参数层层穿透
        Path userSpeechPath = getSession().getUserAudioPath();

        // time to first token，同时也应该是实质上的AssistantMessage createdAt 时间戳。
        // 在ChatModel生成完成时，语音合成器、播放器已经在工作了。但在第一个Token生成前，语音合成器与播放器还没有开始工作。
        // 播放器生成文件时也需要用到一个关联到AssistantMessage的ID，不能在sendStart时创建磁盘音频文件。
        AtomicReference<Instant> ttft = new AtomicReference<>(null);

        String ownerId = conversation.getOwnerId();

        // 从 ToolsSessionHolder 获取实时工具列表（包含后注册的设备 MCP 工具）
        List<ToolCallback> liveTools = getSession().getToolsSessionHolder().getAllFunction();

        // Layer 3: Embedding 预筛选工具子集
        List<ToolCallback> effectiveTools = useFunctionCall ? liveTools : new ArrayList<>();

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(effectiveTools)
                .toolContext(TOOL_CONTEXT_SESSION_ID_KEY, sessionId)
                .toolContext("deviceId", ownerId)
                .toolContext("conversationTimestamp", now.toEpochMilli())
                .build();

        conversation.add(userMessage);
        // 构建运行时上下文（位置等）传入 messages
        ChatSession currentSession = getSession();
        String location = currentSession.getDevice() != null ? currentSession.getDevice().getLocation() : null;
        ConversationContext ctx = new ConversationContext(location);
        List<Message> messages = conversation.messages(ctx);
        Prompt prompt = new Prompt(messages, chatOptions);

        Flux<ChatResponse> chatFlux = chatModel.stream(prompt)
            .doOnError(error -> {
                listener.onError(error);
            });
        chatFlux = chatFlux.doOnNext(chatResponse -> {
            Instant assistantMessageCreatedAt = Instant.now();
            boolean isFirst = ttft.compareAndSet(null, assistantMessageCreatedAt);
            if (isFirst) {
                if (player.getOpusRecorder() != null) {
                    player.getOpusRecorder().setAssistantMessageCreatedAt(assistantMessageCreatedAt);
                }
            }
        });
        return new MessageAggregator().aggregate(chatFlux, chatResponse -> {
            var toolCallDetails = getSession().drainToolCallDetails();
            // 从 DialogueContext 中获取工具调用的中间消息
            AssistantMessage toolCallAssistantMsg = getSession().getDialogueContext().drainToolCallAssistantMessage();
            ToolResponseMessage toolResponseMsg = getSession().getDialogueContext().drainToolResponseMessage();

            DialogueTurn dialogueTurn = DialogueTurn.builder()
                    .userMessage(userMessage)
                    .chatResponse(chatResponse)
                    .conversation(conversation)
                    .userMessageCreatedAt(now)
                    .userSpeechPath(userSpeechPath)
                    .assistantMessageCreatedAt(ttft.get())
                    .toolCallDetails(toolCallDetails)
                    .toolCallAssistantMessage(toolCallAssistantMsg)
                    .toolResponseMessage(toolResponseMsg)
                    .build();
            // UserMessage 的时间戳应在 DialogueTurn 中注入，与 Conversation 持有的是同一个 UserMessage。
            dialogueTurn.injectInstants();
            listener.onDialogueTurn(dialogueTurn);

            // 将工具调用链注入 Conversation 历史
            if (toolCallAssistantMsg != null && toolResponseMsg != null) {
                conversation.addToolCallChain(toolCallAssistantMsg, toolResponseMsg);
            }
            // 不能再从 ChatResponse 里取 AssistantMessage，因为已注入时间戳
            conversation.add(dialogueTurn.getAssistantMessage());
        });
    }

    /**
     * 默认情况下，启用工具调用
     * @param userMessage
     */
    public void chat(String userMessage){
        chat(userMessage,true);
    }

    /**
     * @param userMessage
     * @param useFunctionCall
     */
    public void chat(String userMessage, boolean useFunctionCall){
        Instant now = Instant.now();
        Flux<ChatResponse> chatResponseFlux = chatStream(now, new UserMessage(userMessage), useFunctionCall);
        synthesizer.synthesize(convert(chatResponseFlux));
    }

    /**
     * 检查当前Persona是否处于活跃状态（LLM生成中、TTS合成中、音频播放中等）。
     * 用于打断判断：只要管道中任何一层仍在工作，就应该被打断。
     */
    public boolean isActive() {
        if (synthesizer != null && synthesizer.isActive()) {
            return true;
        }
        return player != null && player.hasContent();
    }

    /**
     * 发送告别语并在播放完成后关闭会话
     *
     * @return 是否成功发送告别语
     */
    public void sendGoodbyeMessage() {
        ChatSession session = getSession();
        if (session == null || !session.isAudioChannelOpen()){
            return ;
        }
        // 告别语不需要保存opus音频文件，重置时间戳防止复用上一轮对话的值
        if (player.getOpusRecorder() != null) {
            player.getOpusRecorder().setAssistantMessageCreatedAt(null);
        }
        player.setFunctionAfterChat(() -> {
            session.setPersona(null);
            session.setPlayer(null);
            conversation.clear();
            if (sessionManager != null) {
                sessionManager.closeSession(session);
            } else {
                session.close();
            }
        });
        if(goodbyeMessages!=null){
            // 随机选择一条告别语
            String goodbyeMessage = goodbyeMessages.get();

            // 直接处理告别语，不通过LLM
            synthesizer.synthesize(goodbyeMessage);
        }else{
            chat("我有事先忙了，再见！",false);
        }

    }

    private Flux<String> convert(Flux<ChatResponse> chatResponseFlux) {
        return chatResponseFlux.mapNotNull(ChatResponse::getResult)
                .mapNotNull(Generation::getOutput)
                .mapNotNull(AssistantMessage::getText);

    }
}

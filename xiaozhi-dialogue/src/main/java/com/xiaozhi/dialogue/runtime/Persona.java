package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.ConversationContext;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.Synthesizer;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.ai.tts.SpeechTokenFilter;
import com.xiaozhi.utils.EmojiUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
 * 用户音频的持久化路径与时长通过 ChatSession.getUserAudioStoredPath()/getSttDuration() 关联到 DialogueTurn，
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

    private static final List<String> ERROR_FALLBACK_MESSAGES = List.of(
            "抱歉，我刚刚走神了，你能再说一遍吗？",
            "哎呀，我这会儿没反应过来，再说一次好吗？",
            "不好意思，刚才没听清，麻烦你再讲一遍。");
    private static final Random FALLBACK_RANDOM = new Random();

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


    /** 当前轮次运行态，打断收尾与播完判定都从这里取 */
    @Builder.Default
    private final AtomicReference<Turn> currentTurn = new AtomicReference<>();

    /** 打断代次：每次打断递增，用于识别"STT 出结果后、chat 接管前"被打断的轮次 */
    @Builder.Default
    private final AtomicLong interruptEpoch = new AtomicLong();

    /** STT 已出结果、chat 尚未接管的轮次数，期间也算活跃 */
    @Builder.Default
    private final AtomicInteger pendingTurns = new AtomicInteger();

    /** 打断触发那一刻的当前轮次与已听到的文本，异步收尾时以它为准 */
    @Builder.Default
    private final AtomicReference<InterruptTarget> interruptTarget = new AtomicReference<>();

    private record InterruptTarget(Turn turn, String spokenText) {}

    // PersonaListener 回调实现了核心与辅助的分离：Persona 只通知"发生了什么"，持久化和监控由外部实现。

    /**
     * 获取ChatSession
     */
    private ChatSession getSession() {
        return sessionManager.getSession(sessionId);
    }

    /**
     * 一轮对话在内存里的运行态，从 chat 入口建立，到完成回调、播完、出错或打断收尾。
     */
    private static final class Turn {
        private final long turnId;
        private final UserMessage userMessage;
        private final Instant startedAt;
        private final String userSpeechStoredPath;
        private final double sttDuration;
        /** 首 token 时刻，也是助手消息的创建时间 */
        private final AtomicReference<Instant> ttft = new AtomicReference<>(null);
        private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.PREPARING);
        /** 完成回调落库的那一轮，播放途中被打断时据此截断 */
        private volatile DialogueTurn completedTurn;

        private Turn(long turnId, UserMessage userMessage, Instant startedAt,
                     String userSpeechStoredPath, double sttDuration) {
            this.turnId = turnId;
            this.userMessage = userMessage;
            this.startedAt = startedAt;
            this.userSpeechStoredPath = userSpeechStoredPath;
            this.sttDuration = sttDuration;
        }
    }

    /**
     * PREPARING：已进 chat，尚未开始生成；GENERATING：LLM 出 token 中；
     * COMPLETED：LLM 完成并已落库，但可能还在播；CLOSED：完整播出或出错收尾，之后的打断与本轮无关；
     * INTERRUPTED：被打断并已收尾
     */
    private enum Phase { PREPARING, GENERATING, COMPLETED, CLOSED, INTERRUPTED }

    /**
     * 处理用户查询（流式方式）。在 synthesize 订阅时才执行，准备期间被打断则不再调 LLM。
     * @param useFunctionCall 是否使用函数调用
     */
    private Flux<ChatResponse> chatStream(Turn turn, boolean useFunctionCall) {
        UserMessage userMessage = turn.userMessage;
        String ownerId = conversation.getOwnerId();

        // 从 ToolsSessionHolder 获取实时工具列表（包含后注册的设备 MCP 工具）
        List<ToolCallback> liveTools = getSession().getToolsSessionHolder().getAllFunction();

        // Layer 3: Embedding 预筛选工具子集
        List<ToolCallback> effectiveTools = useFunctionCall ? liveTools : new ArrayList<>();

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(effectiveTools)
                .toolContext(TOOL_CONTEXT_SESSION_ID_KEY, sessionId)
                .toolContext("deviceId", ownerId)
                .toolContext("conversationTimestamp", turn.turnId)
                .build();

        // 准备期间被打断：用户消息已由打断收尾记入历史，这里不再生成
        if (!turn.phase.compareAndSet(Phase.PREPARING, Phase.GENERATING)) {
            return Flux.empty();
        }

        // 构建运行时上下文
        ChatSession currentSession = getSession();
        String location = currentSession.getDevice() != null ? currentSession.getDevice().getLocation() : null;
        ConversationContext ctx = new ConversationContext(location);
        List<Message> messages = conversation.messages(ctx);
        Prompt prompt = new Prompt(messages, chatOptions);

        Flux<ChatResponse> chatFlux = chatModel.stream(prompt)
            .doOnError(error -> {
                listener.onError(error);
                failTurn(turn);
            });
        chatFlux = chatFlux.doOnNext(chatResponse -> {
            // 首 token 时刻即助手消息创建时间；播放器落盘音频文件也以此关联到助手消息
            Instant assistantMessageCreatedAt = Instant.now();
            boolean isFirst = turn.ttft.compareAndSet(null, assistantMessageCreatedAt);
            if (isFirst && player.getOpusRecorder() != null) {
                player.getOpusRecorder().setAssistantMessageCreatedAt(assistantMessageCreatedAt);
            }
        });
        return new MessageAggregator().aggregate(chatFlux, chatResponse -> completeTurn(turn, chatResponse));
    }

    /**
     * LLM 正常完成：落库并写入历史。与打断收尾互斥，谁先切走阶段谁负责。
     */
    private void completeTurn(Turn turn, ChatResponse chatResponse) {
        synchronized (turn) {
            if (!turn.phase.compareAndSet(Phase.GENERATING, Phase.COMPLETED)) {
                return;
            }
            DialogueContext.ToolCallSnapshot snapshot = getSession().getDialogueContext().snapshotToolCalls(turn.turnId);
            if (snapshot == null) {
                return;
            }
            AssistantMessage assistant = stripMetaTags(chatResponse.getResult().getOutput());
            Usage usage = chatResponse.getMetadata() != null ? chatResponse.getMetadata().getUsage() : null;
            DialogueTurn dialogueTurn = buildTurn(turn, assistant, usage, snapshot, false);
            commitTurn(turn, dialogueTurn, snapshot.chains());
            turn.completedTurn = dialogueTurn;
        }
    }

    /**
     * 方括号元数据标签不进历史
     */
    static AssistantMessage stripMetaTags(AssistantMessage message) {
        String text = message.getText();
        if (text == null) {
            return message;
        }
        String cleaned = EmojiUtils.stripMetaTags(text);
        if (cleaned.equals(text)) {
            return message;
        }
        return AssistantMessage.builder()
                .content(cleaned)
                .properties(message.getMetadata())
                .toolCalls(message.getToolCalls())
                .media(message.getMedia())
                .build();
    }

    /**
     * LLM 出错：轮次到此为止，只记用户消息。之后兜底口播被打断也与本轮无关。
     */
    private void failTurn(Turn turn) {
        synchronized (turn) {
            if (!turn.phase.compareAndSet(Phase.GENERATING, Phase.CLOSED)) {
                return;
            }
            DialogueContext.ToolCallSnapshot snapshot = getSession().getDialogueContext().snapshotToolCalls(turn.turnId);
            DialogueTurn dialogueTurn = buildTurn(turn, null, null, snapshot, false);
            commitTurn(turn, dialogueTurn, snapshot != null ? snapshot.chains() : List.of());
        }
    }

    private DialogueTurn buildTurn(Turn turn, AssistantMessage assistant, Usage usage,
                                   DialogueContext.ToolCallSnapshot snapshot, boolean interrupted) {
        // 本轮模型真实调用的工具链，顺序即持久化顺序
        List<ToolChainPair> allChains = new ArrayList<>();
        if (snapshot != null) {
            allChains.addAll(snapshot.chains());
        }
        Instant assistantCreatedAt = null;
        if (assistant != null) {
            assistantCreatedAt = turn.ttft.get() != null ? turn.ttft.get() : Instant.now();
        }
        return DialogueTurn.builder()
                .userMessage(turn.userMessage)
                .assistantMessage(assistant)
                .usage(usage)
                .conversation(conversation)
                .userMessageCreatedAt(turn.startedAt)
                .userSpeechStoredPath(turn.userSpeechStoredPath)
                .sttDuration(turn.sttDuration)
                .assistantMessageCreatedAt(assistantCreatedAt)
                .toolCallDetails(snapshot != null ? snapshot.details() : List.of())
                .toolChains(allChains)
                .interrupted(interrupted)
                .build();
    }

    /**
     * 落库并写入内存历史：先模型工具链后助手消息，与持久化顺序一致。
     */
    private void commitTurn(Turn turn, DialogueTurn dialogueTurn, List<ToolChainPair> modelChains) {
        // UserMessage 的时间戳应在 DialogueTurn 中注入，与 Conversation 持有的是同一个 UserMessage。
        dialogueTurn.injectInstants();
        listener.onDialogueTurn(dialogueTurn);
        List<Message> tail = new ArrayList<>();
        for (ToolChainPair chain : modelChains) {
            tail.add(chain.toolCallMessage());
            tail.add(chain.toolResponseMessage());
        }
        if (dialogueTurn.getAssistantMessage() != null) {
            tail.add(dialogueTurn.getAssistantMessage());
        }
        if (currentTurn.get() == turn) {
            tail.forEach(conversation::add);
        } else {
            // 迟到的收尾：新一轮已开始，插回本轮用户消息之后
            conversation.insertAfterTurn(turn.userMessage, tail);
        }
    }

    /**
     * 播放器发完 tts stop：LLM 已完成、合成器也已把全部音频交给播放器的轮次到此才算完整播出，
     * 之后的打断（问候语、推送）与本轮无关。
     * 句间断流、工具提示播完、双向 TTS 首帧未到时的 stop 都不算结束：合成器仍活跃。
     */
    private void onPlaybackStopped() {
        Turn turn = currentTurn.get();
        if (turn != null && !synthesizerActive()) {
            turn.phase.compareAndSet(Phase.COMPLETED, Phase.CLOSED);
        }
    }

    private boolean synthesizerActive() {
        return synthesizer != null && synthesizer.isActive();
    }

    /**
     * 打断发生时立刻调用（同步于触发线程）：记下要收尾的轮次并递增代次。
     * 之后再 prepareTurn 的轮次不受影响，之前 prepareTurn 还没进 chat 的轮次会被判为"还没开口就被打断"。
     */
    public void markInterrupted() {
        Turn turn = currentTurn.get();
        // 听到的文本也在此刻定格
        interruptTarget.set(new InterruptTarget(turn, player.spokenText()));
        interruptEpoch.incrementAndGet();
    }

    /**
     * 用户打断后的历史收尾。必须在 synthesizer.cancel() 之后、player.stop() 之前调用，
     * 此时播放器还记着本轮下发到了哪句。
     * 准备中或生成中被打断：听到的部分作为助手消息落库并写入历史，一个字没听到就只留用户消息；
     * 已生成完但没播完：把已落库的助手消息截到听到的位置。
     */
    public void onInterrupted() {
        // 优先收尾打断触发那一刻的轮次；没有记录（从未标记）则取当前轮
        InterruptTarget target = interruptTarget.getAndSet(null);
        Turn turn = target != null ? target.turn() : currentTurn.get();
        if (turn == null || getSession() == null) {
            return;
        }
        String spokenText = target != null ? target.spokenText() : player.spokenText();
        interruptTurn(turn, spokenText);
    }

    private void interruptTurn(Turn turn, String spokenText) {
        synchronized (turn) {
            Phase previous = turn.phase.getAndSet(Phase.INTERRUPTED);
            switch (previous) {
                case PREPARING, GENERATING -> {
                    finishInterruptedTurn(turn, spokenText);
                    // 句柄可能在 abort 的 cancel 之后才交给合成器，这里再 cancel 一次
                    if (synthesizer != null && currentTurn.get() == turn) {
                        synthesizer.cancel();
                    }
                }
                case COMPLETED -> truncateCompletedTurn(turn, spokenText);
                default -> turn.phase.set(previous);
            }
        }
    }

    private void finishInterruptedTurn(Turn turn, String spokenText) {
        DialogueContext.ToolCallSnapshot snapshot = getSession().getDialogueContext().snapshotToolCalls(turn.turnId);
        AssistantMessage assistant = StringUtils.hasText(spokenText) ? new AssistantMessage(spokenText) : null;
        DialogueTurn dialogueTurn = buildTurn(turn, assistant, null, snapshot, true);
        commitTurn(turn, dialogueTurn, snapshot != null ? snapshot.chains() : List.of());
    }

    /**
     * 阶段还是 COMPLETED 就说明没播完（播完会经 onPlaybackStopped 切到 CLOSED），
     * 直接按听到的文本截断，一个字没听到就删掉那条助手消息。
     */
    private void truncateCompletedTurn(Turn turn, String spokenText) {
        DialogueTurn completed = turn.completedTurn;
        if (completed == null || completed.getAssistantMessage() == null) {
            return;
        }
        // 合成器已空闲且播放器队列已全部下发：用户听到了完整回复
        if (!synthesizerActive() && player.isDrained()) {
            turn.phase.set(Phase.CLOSED);
            return;
        }
        AssistantMessage original = completed.getAssistantMessage();
        if (StringUtils.hasText(spokenText)) {
            AssistantMessage truncated = AssistantMessage.builder()
                    .content(spokenText)
                    .properties(original.getMetadata())
                    .build();
            conversation.replace(original, truncated);
        } else {
            conversation.remove(original);
        }
        listener.onDialogueTurnTruncated(conversation, completed.getAssistantMessageCreatedAt(), spokenText);
    }

    /**
     * STT 出结果到 chat 接管之间本轮也算活跃，连说的第二句才能打断第一句。
     * 返回当时的打断代次，chat 时校验：期间被打断过的轮次不再生成，只把用户消息记入历史。
     * 与 {@link #releaseTurn()} 成对调用。
     */
    public long prepareTurn() {
        pendingTurns.incrementAndGet();
        return interruptEpoch.get();
    }

    public void releaseTurn() {
        pendingTurns.decrementAndGet();
    }

    /**
     * 默认情况下，启用工具调用。
     * @param userMessage 纯文本用户消息（便利方法，不带结构化元数据）
     */
    public void chat(String userMessage){
        chat(userMessage, true);
    }

    /**
     * 接收纯文本。内部包装为不带 metadata 的 UserMessage。
     */
    public void chat(String userMessage, boolean useFunctionCall){
        chat(new UserMessage(userMessage), useFunctionCall);
    }

    public void chat(UserMessage userMessage, boolean useFunctionCall){
        chat(userMessage, useFunctionCall, interruptEpoch.get());
    }

    /**
     * 主入口：带元数据（time/speaker/emotion 等在 UserMessage.metadata 里）的对话。
     * 入口就建立轮次并写入用户消息，之后任何时刻的打断都能正确收尾。
     * @param userMessage 已构造好的 Spring AI UserMessage，可附带 metadata
     * @param useFunctionCall 是否启用工具调用
     * @param epoch {@link #prepareTurn()} 返回的打断代次，不一致说明还没开口就被下一句打断
     */
    public void chat(UserMessage userMessage, boolean useFunctionCall, long epoch){
        ChatSession session = getSession();
        Instant now = Instant.now();
        // 用户消息时间取 STT 出结果那一刻（构造时已写入）
        Turn turn = new Turn(now.toEpochMilli(), userMessage, MessageTimeMetadata.getTimeMillis(userMessage),
                session.getUserAudioStoredPath(), session.getSttDuration());
        session.getDialogueContext().startTurn(turn.turnId);
        player.resetSpokenSentences();
        player.setOnPlaybackStopped(this::onPlaybackStopped);
        conversation.add(userMessage);
        currentTurn.set(turn);

        // 还没开口就被下一句打断：只记用户消息，留给下一轮一并回答
        if (epoch != interruptEpoch.get()) {
            interruptTurn(turn, "");
            return;
        }

        // 工具路由、RAG 召回都在订阅时才执行，准备期间被打断就不再调 LLM
        Flux<ChatResponse> chatResponseFlux = Flux.defer(() -> chatStream(turn, useFunctionCall));
        Flux<ChatToken> tokenFlux = convert(chatResponseFlux);
        // 设备对话管道：过滤掉思考内容，只将正式回复传给语音合成，括号舞台指示与元数据标签整组去掉
        Flux<String> speechFlux = SpeechTokenFilter.apply(tokenFlux.filter(ChatToken::isContent).map(ChatToken::text));
        synthesizer.synthesize(withErrorFallback(speechFlux));
        // 订阅期间被打断时句柄尚未交给合成器，这里补一次 cancel
        if (turn.phase.get() == Phase.INTERRUPTED) {
            synthesizer.cancel();
        }
    }

    /**
     * LLM 一个字都没返回就失败时补一句口播，避免设备完全静默让用户干等。
     * 已经开口再失败则直接收尾——中途插一句道歉比沉默更突兀。
     */
    static Flux<String> withErrorFallback(Flux<String> speechFlux) {
        AtomicBoolean answered = new AtomicBoolean(false);
        return speechFlux
                .doOnNext(text -> {
                    if (StringUtils.hasText(text)) {
                        answered.set(true);
                    }
                })
                .onErrorResume(error -> answered.get() ? Flux.empty() : Flux.just(errorFallbackMessage()));
    }

    static String errorFallbackMessage() {
        return ERROR_FALLBACK_MESSAGES.get(FALLBACK_RANDOM.nextInt(ERROR_FALLBACK_MESSAGES.size()));
    }

    /**
     * 检查当前Persona是否处于活跃状态（STT 已出结果待处理、LLM生成中、TTS合成中、音频播放中等）。
     * 用于打断判断：只要管道中任何一层仍在工作，就应该被打断。
     */
    public boolean isActive() {
        if (pendingTurns.get() > 0) {
            return true;
        }
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

    /**
     * 将 ChatResponse 流转换为 ChatToken 流，包含思考内容和正式回复。
     * <p>
     * Spring AI 1.1.0+ 中，启用 reasoningEffort 后，推理内容通过
     * {@code AssistantMessage.getProperties().get("reasoningContent")} 返回。
     */
    private Flux<ChatToken> convert(Flux<ChatResponse> chatResponseFlux) {
        return chatResponseFlux.mapNotNull(ChatResponse::getResult)
                .mapNotNull(Generation::getOutput)
                .flatMap(message -> {
                    List<ChatToken> tokens = new ArrayList<>();
                    Object reasoning = message.getMetadata().get("reasoningContent");
                    if (reasoning instanceof String r && !r.isEmpty()) {
                        tokens.add(ChatToken.thinking(r));
                    }
                    String text = message.getText();
                    if (text != null && !text.isEmpty()) {
                        tokens.add(ChatToken.content(text));
                    }
                    return Flux.fromIterable(tokens);
                });
    }
}

package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.tool.ToolCallActivityRegistry;
import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.Synthesizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型那边卡住（网关丢包、排队、自建服务挂起）时不能让设备永远停在「思考中」。
 * 连接层的读超时管不到「响应头已到、正文半途停吐」，只能在流上计时：
 * 一个字都没出就说一句兜底话术；已经开口了就直接收尾，中途插一句道歉比缺半句更突兀。
 * 等工具结果的那段不算沉默：模型交出工具调用后要等工具执行完才会再出字，工具自己有执行上限。
 */
@ExtendWith(MockitoExtension.class)
class PersonaLlmTimeoutTest {

    private static final Duration FIRST_TOKEN = Duration.ofMillis(300);
    private static final Duration IDLE = Duration.ofMillis(300);

    @Mock
    private SessionManager sessionManager;
    @Mock
    private PersonaListener listener;
    @Mock
    private ChatModel chatModel;
    @Mock
    private ToolsSessionHolder toolsSessionHolder;
    @Mock
    private MessageSender messageSender;
    @Mock
    private FaultNotifier faultNotifier;

    private final ToolCallActivityRegistry activity = new ToolCallActivityRegistry();
    private RecordingSynthesizer synthesizer;
    private Persona persona;

    @BeforeEach
    void setUp() {
        WebSocketSession session = new WebSocketSession("s1");
        session.setToolsSessionHolder(toolsSessionHolder);
        lenient().when(toolsSessionHolder.getAllFunction()).thenReturn(List.of());
        lenient().when(sessionManager.getSession("s1")).thenReturn(session);
        SilentPlayer player = new SilentPlayer(session, messageSender);
        synthesizer = new RecordingSynthesizer(session, player);
        lenient().when(faultNotifier.replyFailed(session)).thenReturn(Optional.of("抱歉，我刚刚走神了"));
        persona = Persona.builder()
                .sessionManager(sessionManager)
                .sessionId("s1")
                .listener(listener)
                .chatModel(chatModel)
                .synthesizer(synthesizer)
                .player(player)
                .conversation(Conversation.of("device", 1, "s1", "role", 1))
                .firstTokenTimeout(FIRST_TOKEN)
                .idleTimeout(IDLE)
                .toolCallActivity(activity)
                .faultNotifier(faultNotifier)
                .build();
        session.setPersona(persona);
        session.setPlayer(player);
    }

    @Test
    void modelThatNeverStartsGetsAFallbackLine() throws InterruptedException {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.never());

        persona.chat(new UserMessage("讲个故事"), false);

        assertThat(synthesizer.finished.await(3, TimeUnit.SECONDS)).as("超时后本轮必须收尾").isTrue();
        // 这句不是回复，走单句口播，不进回复流
        assertThat(synthesizer.texts).containsExactly("抱歉，我刚刚走神了");
        verify(listener, timeout(1000)).onError(isA(TimeoutException.class));
    }

    @Test
    void modelThatStallsMidAnswerIsCutWithoutAnApology() throws InterruptedException {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.concat(Flux.just(response("从前有座山。")), Flux.never()));

        persona.chat(new UserMessage("讲个故事"), false);

        assertThat(synthesizer.finished.await(3, TimeUnit.SECONDS)).as("静默超时后本轮必须收尾").isTrue();
        assertThat(String.join("", synthesizer.texts)).isEqualTo("从前有座山。");
        verify(faultNotifier, never()).replyFailed(any());
        verify(listener, timeout(1000)).onError(isA(TimeoutException.class));
    }

    /** 模型在正常出字就不能被掐断，计时是对「两次输出之间」而不是整轮 */
    @Test
    void steadyOutputLongerThanTheIdleTimeoutIsNotCut() throws InterruptedException {
        Flux<ChatResponse> slowButSteady = Flux.just("一", "二", "三", "四", "五")
                .delayElements(IDLE.dividedBy(2))
                .map(PersonaLlmTimeoutTest::response);
        when(chatModel.stream(any(Prompt.class))).thenReturn(slowButSteady);

        persona.chat(new UserMessage("数到五"), false);

        assertThat(synthesizer.finished.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(String.join("", synthesizer.texts)).isEqualTo("一二三四五");
    }

    /** 模型一上来就调工具：工具执行期间流上没有任何输出，首字超时不能把这段算进去 */
    @Test
    void toolRunningBeforeTheFirstTokenIsNotSilence() throws InterruptedException {
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation ->
                toolThen(invocation.getArgument(0), FIRST_TOKEN.multipliedBy(3), Flux.just(response("查好了。"))));

        persona.chat(new UserMessage("帮我查一下"), false);

        assertThat(synthesizer.finished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(String.join("", synthesizer.texts)).isEqualTo("查好了。");
        verify(listener, never()).onError(isA(TimeoutException.class));
    }

    @Test
    void toolRunningMidAnswerIsNotSilence() throws InterruptedException {
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> Flux.concat(
                Flux.just(response("我查一下。")),
                toolThen(invocation.getArgument(0), IDLE.multipliedBy(3), Flux.just(response("查到了。")))));

        persona.chat(new UserMessage("帮我查一下"), false);

        assertThat(synthesizer.finished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(String.join("", synthesizer.texts)).isEqualTo("我查一下。查到了。");
        verify(listener, never()).onError(isA(TimeoutException.class));
    }

    /** 工具执行完之后模型再不出字，照样按静默超时收尾 */
    @Test
    void silenceAfterTheToolFinishesStillTimesOut() throws InterruptedException {
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> Flux.concat(
                Flux.just(response("我查一下。")),
                toolThen(invocation.getArgument(0), Duration.ofMillis(50), Flux.never())));

        persona.chat(new UserMessage("帮我查一下"), false);

        assertThat(synthesizer.finished.await(5, TimeUnit.SECONDS)).as("静默超时后本轮必须收尾").isTrue();
        assertThat(String.join("", synthesizer.texts)).isEqualTo("我查一下。");
        verify(listener, timeout(1000)).onError(isA(TimeoutException.class));
    }

    /** 照工具调用管理器的做法：执行期间在注册表里登记，键取自本次请求的工具上下文 */
    private Flux<ChatResponse> toolThen(Prompt prompt, Duration toolTime, Flux<ChatResponse> after) {
        String key = ToolCallActivityRegistry.keyOf(((ToolCallingChatOptions) prompt.getOptions()).getToolContext());
        return Mono.fromRunnable(() -> activity.begin(key))
                .then(Mono.delay(toolTime))
                .doOnNext(tick -> activity.end(key))
                .thenMany(after);
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** 记下交给合成器的每一段文本，流结束（正常或出错）时放行 */
    static class RecordingSynthesizer extends Synthesizer {
        final List<String> texts = new CopyOnWriteArrayList<>();
        final CountDownLatch finished = new CountDownLatch(1);

        RecordingSynthesizer(WebSocketSession session, Player player) {
            super(session, null, player, null);
        }

        @Override
        public void synthesize(Flux<String> stringFlux) {
            stringFlux.subscribe(texts::add, e -> finished.countDown(), finished::countDown);
        }

        @Override
        public void synthesize(String text) {
            texts.add(text);
        }

        @Override
        public void cancel() {
        }

        @Override
        public boolean isActive() {
            return finished.getCount() > 0;
        }
    }

    static class SilentPlayer extends Player {
        SilentPlayer(WebSocketSession session, MessageSender sender) {
            super(session, sender);
        }

        @Override
        public void play(Flux<Speech> speechFlux, boolean reply) {
        }
    }
}

package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.Synthesizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LLM 报错时合成器订阅没有 error consumer，用户那边是彻底静默。
 * 一个字都没说出来就失败时，向 FaultNotifier 要一句、作为非回复口播出去（不进回复流、不进历史）；
 * 已经开口再失败则直接收尾。
 */
class PersonaErrorFallbackTest {

    private static final String NOTICE = "抱歉，我刚刚走神了，你能再说一遍吗？";

    private final SessionManager sessionManager = mock(SessionManager.class);
    private final FaultNotifier faultNotifier = mock(FaultNotifier.class);
    private final Synthesizer synthesizer = mock(Synthesizer.class);
    private final WebSocketSession session = new WebSocketSession("s1");
    private Persona persona;

    @BeforeEach
    void setUp() {
        when(sessionManager.getSession("s1")).thenReturn(session);
        when(faultNotifier.replyFailed(session)).thenReturn(Optional.of(NOTICE));
        persona = Persona.builder()
                .sessionManager(sessionManager)
                .sessionId("s1")
                .synthesizer(synthesizer)
                .player(mock(Player.class))
                .conversation(Conversation.of("device", 1, "s1", "role", 1))
                .faultNotifier(faultNotifier)
                .build();
    }

    @Test
    void failureBeforeAnyOutputSaysTheNoticeOutsideTheReply() {
        List<String> reply = persona.withErrorFallback(Flux.error(new IllegalStateException("LLM 挂了")))
                .collectList().block();

        assertThat(reply).isEmpty();
        verify(synthesizer).synthesize(NOTICE);
    }

    @Test
    void failureAfterSpeakingDoesNotAppendApology() {
        List<String> reply = persona.withErrorFallback(
                        Flux.just("今天天气", "不错").concatWith(Flux.error(new IllegalStateException("中途断了"))))
                .collectList().block();

        assertThat(reply).containsExactly("今天天气", "不错");
        verify(faultNotifier, never()).replyFailed(any());
        verify(synthesizer, never()).synthesize(anyString());
    }

    @Test
    void blankOutputStillCountsAsSilent() {
        persona.withErrorFallback(Flux.just("", "   ").concatWith(Flux.error(new IllegalStateException("空转后失败"))))
                .collectList().block();

        // 只吐了空白等于没开口，仍要兜底
        verify(synthesizer).synthesize(NOTICE);
    }

    /** 刚说过一句（限流）或此刻不能说时 FaultNotifier 给空，就不说 */
    @Test
    void nothingIsSaidWhenTheNotifierDeclines() {
        when(faultNotifier.replyFailed(session)).thenReturn(Optional.empty());

        persona.withErrorFallback(Flux.error(new IllegalStateException("LLM 挂了"))).collectList().block();

        verify(synthesizer, never()).synthesize(anyString());
    }

    @Test
    void successfulStreamIsUntouched() {
        List<String> reply = persona.withErrorFallback(Flux.just("你好", "呀")).collectList().block();

        assertThat(reply).containsExactly("你好", "呀");
        verify(faultNotifier, never()).replyFailed(any());
    }
}

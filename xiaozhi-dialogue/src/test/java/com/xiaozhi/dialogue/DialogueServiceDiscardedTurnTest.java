package com.xiaozhi.dialogue;

import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.audio.VadService;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.enums.ListenMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一轮识别没能变成对话（空结果、回声）时必须把会话状态放开：
 * 停在 THINKING 会让 InactiveSessionChecker 永远跳过这条会话，空闲告别与自动关闭就此失效。
 * 上一轮播放已经接管状态（SPEAKING）时不许覆盖。
 */
@ExtendWith(MockitoExtension.class)
class DialogueServiceDiscardedTurnTest {

    private static final String SESSION_ID = "s1";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(3);
    /** 负向断言的观察窗口：本轮线程收尾后再看一小段，确认状态没被改掉 */
    private static final long SETTLE_WINDOW_MS = 300;

    @Mock
    private VadService vadService;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private Persona persona;
    @Mock
    private SttService sttService;
    @Mock
    private Player player;
    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    @InjectMocks
    private DialogueService dialogueService;

    private WebSocketSession session;
    /** STT 出终稿那一刻的会话状态，用来确认用例确实覆盖到了目标场景 */
    private final AtomicReference<DeviceState> stateAtFinalText = new AtomicReference<>();
    private final CountDownLatch sttReturned = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        lenient().when(springSession.getId()).thenReturn(SESSION_ID);
        session = spy(new WebSocketSession(springSession));
        session.setDevice(boundDevice());
        session.setMode(ListenMode.AUTO);
        session.setPersona(persona);
        session.setPlayer(player);
        lenient().when(persona.getSttService()).thenReturn(sttService);
    }

    @Test
    void emptyFinalTextReleasesThinking() throws InterruptedException {
        runDiscardedTurn("");

        // 收句已经把状态推到 THINKING，用例覆盖的正是「思考中拿到空结果」
        assertThat(stateAtFinalText.get()).isEqualTo(DeviceState.THINKING);
        // IDLE 才会被 InactiveSessionChecker 纳入超时判定
        verify(session, timeout(AWAIT_TIMEOUT.toMillis())).transitionTo(DeviceState.IDLE);
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.IDLE);
        verify(persona, never()).prepareTurn();
    }

    @Test
    void echoOfOwnSpeechReleasesThinking() throws InterruptedException {
        when(player.recentlySpoke("等我一下哈。")).thenReturn(true);

        runDiscardedTurn("等我一下哈。");

        assertThat(stateAtFinalText.get()).isEqualTo(DeviceState.THINKING);
        verify(session, timeout(AWAIT_TIMEOUT.toMillis())).transitionTo(DeviceState.IDLE);
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.IDLE);
        verify(persona, never()).prepareTurn();
    }

    @Test
    void playbackStateIsNotOverwrittenByDiscardedTurn() throws InterruptedException {
        // 用户开口后上一轮的 TTS 才到达：状态归播放，本轮作废也不能动它
        runDiscardedTurn("", () -> session.transitionTo(DeviceState.SPEAKING));

        assertThat(stateAtFinalText.get()).isEqualTo(DeviceState.SPEAKING);
        verify(session, after(SETTLE_WINDOW_MS).never()).transitionTo(DeviceState.IDLE);
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.SPEAKING);
    }

    private void runDiscardedTurn(String finalText) throws InterruptedException {
        runDiscardedTurn(finalText, () -> {
        });
    }

    /**
     * 驱动一轮：SPEECH_START 起流 → beforeSpeechEnd → SPEECH_END 收句 → STT 返回 finalText。
     * STT 假体等收句把状态改掉才出终稿，保证用例落在目标场景而不是抢跑。
     * 返回后本轮只剩收尾几行，状态断言用 Mockito 的等待窗口接住。
     */
    private void runDiscardedTurn(String finalText, Runnable beforeSpeechEnd) throws InterruptedException {
        when(sttService.stream(any(), any())).thenAnswer(invocation -> {
            Flux<byte[]> audio = invocation.getArgument(0);
            audio.blockLast(AWAIT_TIMEOUT);
            stateAtFinalText.set(awaitSegmentEnded());
            sttReturned.countDown();
            return SttResult.textOnly(finalText);
        });
        when(vadService.processAudio(eq(SESSION_ID), any(), anyLong()))
                .thenReturn(vadResult(VadService.VadStatus.SPEECH_START),
                        vadResult(VadService.VadStatus.SPEECH_END));

        dialogueService.processAudioData(session, frame());
        beforeSpeechEnd.run();
        dialogueService.processAudioData(session, frame());

        assertThat(sttReturned.await(AWAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }

    /** 轮询到收句把状态从 LISTENING 改掉为止，超时则原样返回当前状态让断言失败 */
    private DeviceState awaitSegmentEnded() {
        long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && session.getDeviceState() == DeviceState.LISTENING) {
            LockSupport.parkNanos(Duration.ofMillis(2).toNanos());
        }
        return session.getDeviceState();
    }

    private static VadService.VadResult vadResult(VadService.VadStatus status) {
        return new VadService.VadResult(status, frame());
    }

    private static byte[] frame() {
        byte[] payload = new byte[60];
        ThreadLocalRandom.current().nextBytes(payload);
        return payload;
    }

    private static DeviceBO boundDevice() {
        DeviceBO device = new DeviceBO();
        device.setDeviceId("94:a9:90:2b:dd:18");
        device.setRoleId(1);
        device.setUserId(1);
        return device;
    }
}

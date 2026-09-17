package com.xiaozhi.dialogue;

import com.xiaozhi.ai.llm.service.IntentService;
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
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.AudioUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 识别失败不是用户没说话：失败的这句还在 VAD 缓冲里，要原样重放一次再决定这轮去留。
 * 重放必须是整句而不是出错点之后的残段；两次都失败时失败前识别到的部分文本要保住；
 * 没有可重放音频时不得多打一次识别；识别超时（开口起 90 秒无任何结果）不重放。
 */
@ExtendWith(MockitoExtension.class)
class DialogueServiceSttRetryTest {

    private static final String SESSION_ID = "retry-session";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(3);

    @Mock
    private VadService vadService;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private IntentService intentService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private StorageServiceFactory storageServiceFactory;
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

    @TempDir
    private Path tempDir;

    private WebSocketSession session;
    private String originalAudioPath;
    private final AtomicInteger sttCalls = new AtomicInteger();
    /** 第二次识别拿到的音频，用来核对重放的是不是整句 */
    private final AtomicReference<List<byte[]>> replayedFrames = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        // AUDIO_PATH 是全局静态字段，生产期由配置注入，测试改完必须还原，否则污染同 JVM 的其他测试
        originalAudioPath = AudioUtils.AUDIO_PATH;
        AudioUtils.AUDIO_PATH = tempDir.resolve("audio").toString() + "/";
        lenient().when(springSession.getId()).thenReturn(SESSION_ID);
        session = spy(new WebSocketSession(springSession));
        session.setDevice(boundDevice());
        session.setMode(ListenMode.AUTO);
        session.setPersona(persona);
        session.setPlayer(player);
        lenient().when(persona.getSttService()).thenReturn(sttService);
        lenient().when(persona.getPlayer()).thenReturn(player);
    }

    @AfterEach
    void tearDown() {
        AudioUtils.AUDIO_PATH = originalAudioPath;
    }

    @Test
    void upstreamFailureReplaysWholeSentenceAndContinuesWithRetriedText() {
        List<byte[]> sentence = List.of(frame(), frame(), frame());
        when(vadService.getPcmData(SESSION_ID)).thenReturn(sentence);

        runTurn(SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR), SttResult.textOnly("你好"));

        verify(persona, timeout(AWAIT_TIMEOUT.toMillis())).chat(any(), eq(true), anyLong());
        assertThat(sttCalls.get()).isEqualTo(2);
        assertThat(replayedFrames.get()).containsExactlyElementsOf(sentence);
        verify(player).sendStt("你好");
    }

    @Test
    void retryIsSkippedWhenNothingIsBuffered() {
        when(vadService.getPcmData(SESSION_ID)).thenReturn(List.of());

        runTurn(SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR), SttResult.textOnly("不该用到"));

        verify(session, timeout(AWAIT_TIMEOUT.toMillis())).transitionTo(DeviceState.IDLE);
        assertThat(sttCalls.get()).isEqualTo(1);
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.IDLE);
        verify(persona, never()).prepareTurn();
    }

    @Test
    void timeoutIsNotRetried() {
        lenient().when(vadService.getPcmData(SESSION_ID)).thenReturn(List.of(frame()));

        runTurn(SttResult.failure(SttResult.FAILURE_TIMEOUT), SttResult.textOnly("不该用到"));

        verify(session, timeout(AWAIT_TIMEOUT.toMillis())).transitionTo(DeviceState.IDLE);
        assertThat(sttCalls.get()).isEqualTo(1);
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.IDLE);
        verify(persona, never()).prepareTurn();
    }

    @Test
    void partialTextSurvivesWhenRetryFailsToo() {
        when(vadService.getPcmData(SESSION_ID)).thenReturn(List.of(frame()));

        runTurn(SttResult.textOnly("打开").withFailure(SttResult.FAILURE_UPSTREAM_ERROR),
                SttResult.failure(SttResult.FAILURE_TIMEOUT));

        verify(player, timeout(AWAIT_TIMEOUT.toMillis())).sendStt("打开");
        assertThat(sttCalls.get()).isEqualTo(2);
    }

    @Test
    void turnIsDiscardedWhenBothAttemptsFailWithoutText() {
        when(vadService.getPcmData(SESSION_ID)).thenReturn(List.of(frame()));

        runTurn(SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR), SttResult.failure(SttResult.FAILURE_LOCAL_ERROR));

        verify(session, timeout(AWAIT_TIMEOUT.toMillis())).transitionTo(DeviceState.IDLE);
        assertThat(sttCalls.get()).isEqualTo(2);
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.IDLE);
        verify(persona, never()).prepareTurn();
        verify(player, never()).sendStt(anyString());
    }

    /**
     * 驱动一轮：SPEECH_START 起流 → SPEECH_END 收句 → 第一次识别返回 first，
     * 重放那一次返回 second 并记下拿到的音频。本轮在后台线程收尾，断言用 Mockito 的等待窗口接住。
     */
    private void runTurn(SttResult first, SttResult second) {
        when(sttService.stream(any(), any())).thenAnswer(invocation -> {
            Flux<byte[]> audio = invocation.getArgument(0);
            if (sttCalls.incrementAndGet() == 1) {
                audio.blockLast(AWAIT_TIMEOUT);
                return first;
            }
            replayedFrames.set(audio.collectList().block(AWAIT_TIMEOUT));
            return second;
        });
        when(vadService.processAudio(eq(SESSION_ID), any(), anyLong()))
                .thenReturn(vadResult(VadService.VadStatus.SPEECH_START),
                        vadResult(VadService.VadStatus.SPEECH_END));

        dialogueService.processAudioData(session, frame());
        dialogueService.processAudioData(session, frame());
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

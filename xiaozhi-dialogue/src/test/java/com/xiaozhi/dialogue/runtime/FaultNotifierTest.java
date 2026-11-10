package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.playback.FaultNotice;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.playback.Synthesizer;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.utils.DateUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 故障提示的几条规矩：同一会话 30 秒内只播一次（识别坏掉时噪声也会被当成说话，不限流设备会一直道歉）；
 * 有角色就用角色的声音；会话没建起来时用默认音色；连接已断或没有播放器时不播。
 */
@ExtendWith(MockitoExtension.class)
class FaultNotifierTest {

    private static final Instant START = Instant.parse("2026-09-22T10:00:00Z");

    @Mock
    private TtsServiceFactory ttsFactory;
    @Mock
    private TtsService defaultTts;
    @Mock
    private Persona persona;
    @Mock
    private Synthesizer synthesizer;
    @Mock
    private Player player;
    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    private FaultNotifier notifier;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        clockAt(START);
        notifier = new FaultNotifier();
        ReflectionTestUtils.setField(notifier, "ttsFactory", ttsFactory);
        lenient().when(springSession.getId()).thenReturn("s-1");
        lenient().when(springSession.isOpen()).thenReturn(true);
        session = new WebSocketSession(springSession);
        session.setPlayer(player);
        session.setPersona(persona);
        lenient().when(persona.getSynthesizer()).thenReturn(synthesizer);
    }

    @AfterEach
    void resetClock() {
        DateUtils.reset();
    }

    @Test
    void speaksOneOfTheNoticesPhrasesInTheRolesVoice() {
        notifier.notice(session, FaultNotice.MISHEARD);

        verify(synthesizer).synthesize(argThat(
                (String text) -> FaultNotice.MISHEARD.phrases().contains(text)));
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.SPEAKING);
        verify(ttsFactory, never()).getDefaultTtsService();
    }

    @Test
    void noticesAreThrottledPerSession() {
        notifier.notice(session, FaultNotice.MISHEARD);
        clockAt(START.plus(FaultNotifier.NOTICE_INTERVAL).minusSeconds(1));
        notifier.notice(session, FaultNotice.CANNOT_HEAR);
        verify(synthesizer, times(1)).synthesize(anyString());

        clockAt(START.plus(FaultNotifier.NOTICE_INTERVAL).plusSeconds(1));
        notifier.notice(session, FaultNotice.CANNOT_HEAR);
        verify(synthesizer, times(2)).synthesize(anyString());
    }

    /** 会话没建起来就没有角色的合成器，只能用默认音色说 */
    @Test
    void sessionWithoutAPersonaUsesTheDefaultVoice() {
        session.setPersona(null);
        when(ttsFactory.getDefaultTtsService()).thenReturn(defaultTts);

        notifier.notice(session, FaultNotice.UNAVAILABLE);

        verify(ttsFactory).getDefaultTtsService();
    }

    @Test
    void closedConnectionIsLeftAlone() {
        when(springSession.isOpen()).thenReturn(false);

        notifier.notice(session, FaultNotice.MISHEARD);

        verify(synthesizer, never()).synthesize(anyString());
    }

    @Test
    void sessionWithoutAPlayerIsLeftAlone() {
        session.setPlayer(null);

        notifier.notice(session, FaultNotice.MISHEARD);

        verify(synthesizer, never()).synthesize(anyString());
    }

    private static void clockAt(Instant instant) {
        DateUtils.use(Clock.fixed(instant, ZoneOffset.UTC));
    }
}

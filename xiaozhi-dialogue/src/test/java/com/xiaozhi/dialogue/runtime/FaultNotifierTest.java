package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.utils.DateUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * 连续识别失败要升级说法；有角色就用角色的声音，没有角色时用默认音色；
 * 连接已断、没有播放器、设备正在说别的话时不播。设备状态交给播放器在真正开播时切。
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
    }

    @AfterEach
    void resetClock() {
        DateUtils.reset();
    }

    @Test
    void speaksOneOfTheNoticesPhrasesInTheRolesVoice() {
        assertThat(notifier.onSessionUnavailable(session)).isTrue();

        verify(persona).say(argThat(text -> FaultNotice.UNAVAILABLE.phrases().contains(text)));
        verify(ttsFactory, never()).getDefaultTtsService();
        // 状态由播放器开播时切，这里预占了就可能留下没有出口的 SPEAKING
        assertThat(session.getDeviceState()).isNotEqualTo(DeviceState.SPEAKING);
    }

    @Test
    void repeatedSttFailuresEscalateAndSuccessResetsTheCount() {
        notifier.onSttFailed(session);
        verify(persona).say(argThat(text -> FaultNotice.MISHEARD.phrases().contains(text)));

        clockAt(START.plus(FaultNotifier.NOTICE_INTERVAL).plusSeconds(1));
        notifier.onSttFailed(session);
        verify(persona).say(argThat(text -> FaultNotice.CANNOT_HEAR.phrases().contains(text)));

        notifier.onSttSucceeded(session);
        assertThat(session.getDialogueContext().recordSttFailure()).isEqualTo(1);
    }

    @Test
    void noticesAreThrottledPerSession() {
        notifier.onSttUnavailable(session);
        clockAt(START.plus(FaultNotifier.NOTICE_INTERVAL).minusSeconds(1));
        assertThat(notifier.onDialogueFailed(session)).isFalse();
        verify(persona, times(1)).say(anyString());

        clockAt(START.plus(FaultNotifier.NOTICE_INTERVAL).plusSeconds(1));
        assertThat(notifier.onDialogueFailed(session)).isTrue();
        verify(persona, times(2)).say(anyString());
    }

    /** 会话没建起来就没有角色，只能用默认音色说 */
    @Test
    void sessionWithoutAPersonaUsesTheDefaultVoice() {
        session.setPersona(null);
        when(ttsFactory.getDefaultTtsService()).thenReturn(defaultTts);

        assertThat(notifier.onSessionUnavailable(session)).isTrue();

        verify(ttsFactory).getDefaultTtsService();
    }

    /** 设备正在说别的话（如上一轮回复）时不插一句道歉 */
    @Test
    void deviceSpeakingSomethingElseIsLeftAlone() {
        when(player.hasContent()).thenReturn(true);

        assertThat(notifier.onSttFailed(session)).isFalse();

        verify(persona, never()).say(anyString());
    }

    @Test
    void failedReplyIsHandedBackForThePersonaToSay() {
        assertThat(notifier.replyFailed(session))
                .hasValueSatisfying(text -> assertThat(FaultNotice.DISTRACTED.phrases()).contains(text));
        // 同样受限流：刚说过一句就不再给
        assertThat(notifier.replyFailed(session)).isEmpty();
        verify(persona, never()).say(anyString());
    }

    @Test
    void closedConnectionIsLeftAlone() {
        when(springSession.isOpen()).thenReturn(false);

        assertThat(notifier.onSttFailed(session)).isFalse();

        verify(persona, never()).say(anyString());
    }

    @Test
    void sessionWithoutAPlayerIsLeftAlone() {
        session.setPlayer(null);

        assertThat(notifier.onSttFailed(session)).isFalse();

        verify(persona, never()).say(anyString());
    }

    private static void clockAt(Instant instant) {
        DateUtils.use(Clock.fixed(instant, ZoneOffset.UTC));
    }
}

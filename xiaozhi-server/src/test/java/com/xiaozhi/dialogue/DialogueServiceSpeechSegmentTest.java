package com.xiaozhi.dialogue;

import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.enums.DeviceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * 收句必须无条件终结音频流：用户开口后上一轮 TTS 才到达时状态会被改成 SPEAKING，
 * 按 LISTENING 守卫会整段跳过，STT 侧永远等不到流结束，本轮说的话整句丢失。
 */
@ExtendWith(MockitoExtension.class)
class DialogueServiceSpeechSegmentTest {

    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    @InjectMocks
    private DialogueService dialogueService;

    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        lenient().when(springSession.getId()).thenReturn("session-1");
        session = new WebSocketSession(springSession);
        session.createAudioStream();
    }

    private AtomicBoolean subscribeCompletion() {
        AtomicBoolean completed = new AtomicBoolean(false);
        session.getAudioSinks().asFlux().subscribe(data -> {}, error -> {}, () -> completed.set(true));
        return completed;
    }

    @Test
    void completesStreamWhenPreviousTtsTookOverState() {
        session.setDeviceState(DeviceState.SPEAKING);
        AtomicBoolean completed = subscribeCompletion();

        dialogueService.completeSpeechSegment(session);

        assertThat(completed).isTrue();
        // 正在播放的上一轮回复不受影响，打断交给 ASR 首字逻辑
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.SPEAKING);
    }

    @Test
    void completesStreamAndEntersThinkingWhenListening() {
        session.setDeviceState(DeviceState.LISTENING);
        AtomicBoolean completed = subscribeCompletion();

        dialogueService.completeSpeechSegment(session);

        assertThat(completed).isTrue();
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.THINKING);
    }
}

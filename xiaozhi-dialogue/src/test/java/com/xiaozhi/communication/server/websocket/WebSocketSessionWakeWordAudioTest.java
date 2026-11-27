package com.xiaozhi.communication.server.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * 唤醒词前置音频只做采集：设备在 listen/start 之前补发这段音频，
 * 送进识别链路会被识别成唤醒词再触发一轮多余对话。
 */
@ExtendWith(MockitoExtension.class)
class WebSocketSessionWakeWordAudioTest {

    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        lenient().when(springSession.getId()).thenReturn("session-1");
        session = new WebSocketSession(springSession);
    }

    @Test
    void drainReturnsFramesInOrderAndClears() {
        session.addWakeWordAudio(new byte[] {1});
        session.addWakeWordAudio(new byte[] {2});

        List<byte[]> frames = session.drainWakeWordAudio();

        assertThat(frames).hasSize(2);
        assertThat(frames.get(0)).containsExactly(1);
        assertThat(frames.get(1)).containsExactly(2);
        assertThat(session.drainWakeWordAudio()).isEmpty();
    }

    @Test
    void drainOnEmptyBufferReturnsEmptyList() {
        assertThat(session.drainWakeWordAudio()).isEmpty();
    }

    @Test
    void abnormalTrafficIsCapped() {
        for (int i = 0; i < 500; i++) {
            session.addWakeWordAudio(new byte[] {(byte) i});
        }

        assertThat(session.drainWakeWordAudio()).hasSize(100);
    }
}

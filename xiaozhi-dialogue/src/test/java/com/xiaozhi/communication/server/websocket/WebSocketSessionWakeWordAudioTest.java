package com.xiaozhi.communication.server.websocket;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.utils.AudioUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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

    @Test
    void wakeWordAudioIsSavedAsWavLikeUserAudio() {
        DeviceBO device = new DeviceBO();
        device.setDeviceId("94:a9:90:2b:dd:18");
        device.setRoleId(1);
        session.setDevice(device);
        Instant instant = Instant.parse("2026-09-02T12:00:00Z");
        // AUDIO_PATH 由 Spring 配置注入，单测里手动给值
        String originalAudioPath = AudioUtils.AUDIO_PATH;
        AudioUtils.AUDIO_PATH = "audio/";
        try {
            String wakeWordPath = session.getAudioPath("wakeword", instant).toString();

            assertThat(wakeWordPath).endsWith("-wakeword.wav");
            // MAC 地址里的冒号不能进路径
            assertThat(wakeWordPath).contains("94-a9-90-2b-dd-18");
            // assistant 仍是 opus 流直接落盘
            assertThat(session.getAudioPath(MessageBO.SENDER_ASSISTANT, instant).toString())
                    .endsWith("-assistant.ogg");
            assertThat(session.getAudioPath(MessageBO.SENDER_USER, instant).toString()).endsWith("-user.wav");
        } finally {
            AudioUtils.AUDIO_PATH = originalAudioPath;
        }
    }
}

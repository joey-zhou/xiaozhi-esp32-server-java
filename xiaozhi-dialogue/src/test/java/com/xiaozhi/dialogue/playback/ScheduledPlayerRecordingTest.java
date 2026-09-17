package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.utils.AudioUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 播放器与录音组件的收尾时序：自然播完和被打断都必须把 OGG 文件关掉，
 * 中途不关会留下写了一半的损坏文件。
 * 另钉住服务端 AEC 的参考信号来源：下发帧带出的是它自己编码前的那段 PCM。
 */
class ScheduledPlayerRecordingTest {

    private MessageSender sender;
    private OpusRecorder recorder;
    private ScheduledPlayer player;

    @BeforeEach
    void setUp() {
        sender = mock(MessageSender.class);
        recorder = mock(OpusRecorder.class);
        org.springframework.web.socket.WebSocketSession springSession =
                mock(org.springframework.web.socket.WebSocketSession.class);
        lenient().when(springSession.getId()).thenReturn("s1");
        player = new ScheduledPlayer(new WebSocketSession(springSession), sender);
        player.setOpusRecorder(recorder);
    }

    @AfterEach
    void tearDown() {
        player.stop();
    }

    @Test
    void naturalStopClosesRecordingAfterFrames() {
        player.play(Flux.just(Speech.ofOpus(new byte[]{1}, "一句话。"), Speech.ofOpus(new byte[]{2})), true);

        verify(sender, timeout(5000)).sendTtsMessage(any(), isNull(), eq("stop"));
        // 收尾录音排在 tts stop 下发之后，只等 sendTtsMessage 会抢在 onSendStop 之前断言
        verify(recorder, timeout(5000)).onSendStop();

        InOrder inOrder = inOrder(recorder);
        inOrder.verify(recorder).onSendStart();
        inOrder.verify(recorder, times(2)).onSendOpusFrame(any(), any(), anyLong());
        inOrder.verify(recorder).onSendStop();
    }

    // 参考信号必须是这一帧编码前的原始 PCM，取错会让 AEC 拿着对不上的信号做滤波
    @Test
    void referencePcmIsTheSamplesThatWereEncoded() {
        when(recorder.needsReferencePcm()).thenReturn(true);
        byte[] pcm = tone(AudioUtils.FRAME_SIZE);

        player.play(Flux.just(new Speech(pcm, "一句话。")), true);

        ArgumentCaptor<byte[]> reference = ArgumentCaptor.forClass(byte[].class);
        verify(recorder, timeout(5000)).onSendOpusFrame(any(), reference.capture(), anyLong());
        assertThat(reference.getValue()).isEqualTo(pcm);
    }

    // 没做服务端 AEC 的会话不留参考 PCM：待发队列里每帧多背一份 PCM 是纯浪费
    @Test
    void referencePcmIsNotRetainedWithoutServerAec() {
        player.play(Flux.just(new Speech(tone(AudioUtils.FRAME_SIZE), "一句话。")), true);

        verify(recorder, timeout(5000)).onSendOpusFrame(any(), isNull(), anyLong());
    }

    /** 指定样本数的16bit小端单声道正弦 */
    private static byte[] tone(int samples) {
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            short v = (short) (Math.sin(2 * Math.PI * 440 * i / AudioUtils.SAMPLE_RATE) * 12000);
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return pcm;
    }

    @Test
    void interruptClosesRecordingMidPlayback() {
        player.play(Flux.just(Speech.ofOpus(new byte[]{1}, "一句话。")), true);
        verify(recorder, timeout(5000)).onSendOpusFrame(any(), any(), anyLong());

        player.stop();

        verify(recorder).closeOpusFile();
    }
}

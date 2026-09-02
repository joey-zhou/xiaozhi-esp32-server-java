package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import reactor.core.publisher.Flux;

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

/**
 * 播放器与录音组件的收尾时序：自然播完和被打断都必须把 OGG 文件关掉，
 * 中途不关会留下写了一半的损坏文件。
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

        InOrder inOrder = inOrder(recorder);
        inOrder.verify(recorder).onSendStart();
        inOrder.verify(recorder, times(2)).onSendOpusFrame(any(), anyLong());
        inOrder.verify(recorder).onSendStop();
    }

    @Test
    void interruptClosesRecordingMidPlayback() {
        player.play(Flux.just(Speech.ofOpus(new byte[]{1}, "一句话。")), true);
        verify(recorder, timeout(5000)).onSendOpusFrame(any(), anyLong());

        player.stop();

        verify(recorder).closeOpusFile();
    }
}

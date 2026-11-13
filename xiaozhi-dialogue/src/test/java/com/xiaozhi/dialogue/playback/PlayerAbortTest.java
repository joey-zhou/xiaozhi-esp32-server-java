package com.xiaozhi.dialogue.playback;

import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** tts stop 只由播放器发：打断时停播出错也要告诉设备停播，否则设备一直停在说话状态不重开麦 */
class PlayerAbortTest {

    @Test
    void abortTellsTheDeviceToStopEvenWhenStoppingFails() {
        MessageSender sender = mock(MessageSender.class);
        org.springframework.web.socket.WebSocketSession springSession =
                mock(org.springframework.web.socket.WebSocketSession.class);
        lenient().when(springSession.getId()).thenReturn("s1");
        WebSocketSession session = new WebSocketSession(springSession);
        ScheduledPlayer player = spy(new ScheduledPlayer(session, sender));
        OpusRecorder recorder = mock(OpusRecorder.class);
        player.setOpusRecorder(recorder);
        doThrow(new IllegalStateException("录音收尾出错")).when(player).stop();
        AtomicBoolean afterGoodbye = new AtomicBoolean();
        player.setFunctionAfterChat(() -> afterGoodbye.set(true));

        player.abort();

        verify(sender).sendTtsMessage(session, null, "stop");
        verify(recorder).onSendStop();
        // 告别途中被打断，告别后的收尾（关会话）照做
        assertThat(afterGoodbye).isTrue();
    }
}

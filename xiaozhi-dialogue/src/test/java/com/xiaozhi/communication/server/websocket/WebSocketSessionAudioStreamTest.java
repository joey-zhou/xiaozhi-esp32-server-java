package com.xiaozhi.communication.server.websocket;

import com.xiaozhi.enums.DeviceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;

/**
 * 音频流关闭必须终结流：只释放引用会让订阅该流的 STT 永远等不到结束信号，
 * 连接被服务端超时断开，发送线程与订阅链一起泄漏。
 */
@ExtendWith(MockitoExtension.class)
class WebSocketSessionAudioStreamTest {

    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        lenient().when(springSession.getId()).thenReturn("session-1");
        session = new WebSocketSession(springSession);
    }

    private AtomicBoolean subscribeCompletion() {
        AtomicBoolean completed = new AtomicBoolean(false);
        session.getAudioSinks().asFlux().subscribe(data -> {}, error -> {}, () -> completed.set(true));
        return completed;
    }

    @Test
    void closeAudioStreamTerminatesSubscriber() {
        session.createAudioStream();
        AtomicBoolean completed = subscribeCompletion();

        session.closeAudioStream();

        assertThat(completed).isTrue();
        assertThat(session.getAudioSinks()).isNull();
    }

    @Test
    void startingNewTurnTerminatesPreviousStream() {
        session.createAudioStream();
        AtomicBoolean previousCompleted = subscribeCompletion();

        // startStt 每轮都是先 close 再 create
        session.closeAudioStream();
        session.createAudioStream();

        assertThat(previousCompleted).isTrue();
        assertThat(session.getAudioSinks()).isNotNull();
    }

    @Test
    void clearAudioSinksTerminatesStreamAndResetsState() {
        session.createAudioStream();
        session.setDeviceState(DeviceState.LISTENING);
        AtomicBoolean completed = subscribeCompletion();

        session.clearAudioSinks();

        assertThat(completed).isTrue();
        assertThat(session.getAudioSinks()).isNull();
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.IDLE);
    }

    @Test
    void closeAudioStreamIsSafeWithoutStream() {
        assertThatCode(() -> session.closeAudioStream()).doesNotThrowAnyException();
    }
}

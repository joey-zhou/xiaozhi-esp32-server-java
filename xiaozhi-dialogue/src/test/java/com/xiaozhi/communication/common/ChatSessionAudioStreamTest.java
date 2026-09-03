package com.xiaozhi.communication.common;

import com.xiaozhi.communication.server.websocket.WebSocketSession;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 钉住音频流写入的串行化。送帧在 VAD 线程、收句在识别线程，
 * Reactor 的 tryEmit* 检测到并发就返回 FAIL_NON_SERIALIZED 把信号丢掉。
 */
class ChatSessionAudioStreamTest {

    /** 并发窗口靠轮数覆盖，单轮撞不上不代表没有竞态 */
    private static final int ROUNDS = 200;
    private static final int FRAMES_PER_ROUND = 40;
    private static final Duration AWAIT = Duration.ofSeconds(2);

    /**
     * 收句被丢掉时订阅方永远等不到结束信号：这一轮说的话整句丢失，
     * 识别线程一直挂到 provider 自己的超时，会话停在 THINKING 再不接受新的一轮。
     */
    @Test
    void completeSurvivesFramesArrivingOnAnotherThread() throws InterruptedException {
        for (int round = 0; round < ROUNDS; round++) {
            ChatSession session = new WebSocketSession("session-" + round);
            session.createAudioStream();
            Flux<byte[]> audio = session.getAudioSinks().asFlux();

            CountDownLatch feeding = new CountDownLatch(1);
            Thread feeder = Thread.startVirtualThread(() -> {
                feeding.countDown();
                for (int i = 0; i < FRAMES_PER_ROUND; i++) {
                    session.sendAudioData(frame());
                }
            });

            feeding.await();
            session.completeAudioStream();
            feeder.join();

            int current = round;
            assertThatCode(() -> audio.blockLast(AWAIT))
                    .withFailMessage("第 %d 轮收句被丢弃，订阅方没等到结束信号", current)
                    .doesNotThrowAnyException();
        }
    }

    private static byte[] frame() {
        return new byte[]{1, 2, 3};
    }
}

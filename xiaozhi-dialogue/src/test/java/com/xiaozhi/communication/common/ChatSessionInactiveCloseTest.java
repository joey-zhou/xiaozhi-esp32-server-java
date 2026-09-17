package com.xiaozhi.communication.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「超时告别只发一次」这件事真正的实现是 {@link ChatSession#tryBeginInactiveClose()} 里的 CAS。
 * <p>
 * {@link InactiveSessionCheckerTest} 把整个 ChatSession mock 掉、直接 stub 了这个方法的返回值，
 * 所以那边测的是「检查器信不信这个返回值」，而不是「同一会话会不会被触发两次」。
 * 真把 CAS 改成非原子的读改写，那边照样全绿，设备就会连着收到两句告别语并被关两次。
 * 本类只钉这个 CAS 本身。
 */
class ChatSessionInactiveCloseTest {

    /** ChatSession 是抽象类，这里只需要它的状态位，通道相关的抽象方法给最小实现 */
    private static ChatSession session(String sessionId) {
        return new ChatSession(sessionId) {
            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public boolean isAudioChannelOpen() {
                return true;
            }

            @Override
            public void close() {
            }

            @Override
            public void sendTextMessage(String message) {
            }

            @Override
            public void sendBinaryMessage(byte[] message, long timestamp) {
            }
        };
    }

    @Test
    void onlyTheFirstCallWinsTheInactiveClose() {
        ChatSession session = session("s-1");

        assertThat(session.tryBeginInactiveClose()).isTrue();
        assertThat(session.tryBeginInactiveClose()).isFalse();
        assertThat(session.tryBeginInactiveClose()).isFalse();
    }

    @Test
    void resetAllowsTheNextRoundToTriggerAgain() {
        ChatSession session = session("s-2");

        assertThat(session.tryBeginInactiveClose()).isTrue();
        session.resetInactiveClosing();

        assertThat(session.tryBeginInactiveClose())
            .as("会话重新活跃后必须能再次进入超时关闭，否则设备第二次挂机永远等不到告别语")
            .isTrue();
    }

    @Test
    void concurrentCheckersProduceExactlyOneWinner() throws Exception {
        ChatSession session = session("s-3");
        int threads = 64;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();

        IntStream.range(0, threads).forEach(i -> Thread.startVirtualThread(() -> {
            try {
                start.await();
                if (session.tryBeginInactiveClose()) {
                    winners.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        }));

        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(winners.get())
            .as("多个实例/多轮定时同时判到超时，只能有一个真的去发告别语并关会话")
            .isEqualTo(1);
    }
}

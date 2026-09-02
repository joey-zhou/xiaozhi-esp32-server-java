package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.utils.OpusProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 用户开口后播放先停住，终稿决定续播还是真打断。停住期间不发真帧、不开新句；
 * 开播后的停住、句间、上游断流都按节拍发静音帧，设备播放时间轴不断。
 */
class ScheduledPlayerPauseTest {

    private MessageSender sender;
    private ScheduledPlayer player;
    private final AtomicInteger framesSent = new AtomicInteger();
    private final AtomicInteger silenceSent = new AtomicInteger();

    @BeforeEach
    void setUp() {
        sender = mock(MessageSender.class);
        doAnswer(inv -> {
            byte[] frame = inv.getArgument(1);
            if (Arrays.equals(frame, OpusProcessor.silenceFrame())) {
                silenceSent.incrementAndGet();
            } else {
                framesSent.incrementAndGet();
            }
            return null;
        }).when(sender).sendBinaryMessage(any(), any(), anyLong());
        org.springframework.web.socket.WebSocketSession springSession =
                mock(org.springframework.web.socket.WebSocketSession.class);
        lenient().when(springSession.getId()).thenReturn("s1");
        player = new ScheduledPlayer(new WebSocketSession(springSession), sender);
    }

    @AfterEach
    void tearDown() {
        player.stop();
    }

    private static Flux<Speech> frames(int count) {
        return frames(count, "第一句。");
    }

    private static Flux<Speech> frames(int count, String text) {
        List<Speech> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte[] data = new byte[]{(byte) i};
            list.add(i == 0 && text != null ? Speech.ofOpus(data, text) : Speech.ofOpus(data));
        }
        return Flux.fromIterable(list);
    }

    private static void awaitAtLeast(AtomicInteger counter, int expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (counter.get() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(counter.get()).isGreaterThanOrEqualTo(expected);
    }

    @Test
    void pauseBeforePlayDefersStartUntilResume() throws InterruptedException {
        player.pause(5000);
        player.play(frames(5), true);

        Thread.sleep(250);
        assertThat(framesSent.get()).isZero();
        assertThat(silenceSent.get()).isZero();
        verify(sender, never()).sendTtsMessage(any(), isNull(), eq("start"));

        player.resume();

        awaitAtLeast(framesSent, 5, 2000);
        verify(sender).sendTtsMessage(any(), isNull(), eq("start"));
    }

    @Test
    void pauseMidPlaybackHoldsFramesAndFillsSilenceThenResumes() throws InterruptedException {
        player.play(frames(40), true);
        Thread.sleep(300);

        player.pause(5000);
        Thread.sleep(150);
        int sentAtPause = framesSent.get();
        assertThat(sentAtPause).isBetween(3, 12);
        assertThat(player.isPaused()).isTrue();

        int silenceAtPause = silenceSent.get();
        Thread.sleep(400);
        assertThat(framesSent.get()).isEqualTo(sentAtPause);
        assertThat(silenceSent.get() - silenceAtPause).isGreaterThanOrEqualTo(4);
        assertThat(player.hasContent()).isTrue();

        player.resume();

        awaitAtLeast(framesSent, 40, 4000);
        verify(sender, timeout(1000)).sendTtsMessage(any(), isNull(), eq("stop"));
    }

    @Test
    void pauseAutoResumesAfterDeadline() throws InterruptedException {
        player.pause(300);
        player.play(frames(3), true);

        awaitAtLeast(framesSent, 3, 2000);
    }

    @Test
    void stopWhilePausedReleasesSenderThread() throws InterruptedException {
        player.pause(5000);
        player.play(frames(3), true);
        Thread.sleep(100);

        player.stop();
        Thread.sleep(200);

        assertThat(framesSent.get()).isZero();
        assertThat(silenceSent.get()).isZero();
        assertThat(player.isPaused()).isFalse();
        assertThat(player.hasContent()).isFalse();
    }

    @Test
    void pauseDuringSentenceGapHoldsNextSentence() throws InterruptedException {
        player.play(frames(3, "第一句。"), true);
        player.play(frames(3, "第二句。"), true);
        awaitAtLeast(framesSent, 3, 1000);

        player.pause(5000);
        Thread.sleep(500);

        assertThat(framesSent.get()).isEqualTo(3);
        verify(sender, never()).sendTtsMessage(any(), eq("第二句。"), eq("sentence_start"));
        assertThat(silenceSent.get()).isGreaterThanOrEqualTo(4);

        player.resume();

        verify(sender, timeout(2000)).sendTtsMessage(any(), eq("第二句。"), eq("sentence_start"));
        awaitAtLeast(framesSent, 6, 2000);
        verify(sender, timeout(1000)).sendTtsMessage(any(), isNull(), eq("stop"));
    }

    @Test
    void sentenceGapIsFilledWithSilenceAndTrailingGapIsSkipped() throws InterruptedException {
        player.play(frames(3, "第一句。"), true);
        player.play(frames(3, "第二句。"), true);

        verify(sender, timeout(3000)).sendTtsMessage(any(), isNull(), eq("stop"));
        assertThat(framesSent.get()).isEqualTo(6);
        assertThat(silenceSent.get()).isBetween(4, 8);
    }

    @Test
    void upstreamStallIsFilledWithSilence() throws InterruptedException {
        Flux<Speech> stalled = Flux.concat(
                frames(2, "第一句。"),
                Mono.delay(Duration.ofMillis(400)).thenMany(frames(2, null)));
        player.play(stalled, true);

        verify(sender, timeout(3000)).sendTtsMessage(any(), isNull(), eq("stop"));
        assertThat(framesSent.get()).isEqualTo(4);
        assertThat(silenceSent.get()).isBetween(3, 9);
    }
}

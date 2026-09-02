package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.utils.OpusProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 用户开口后播放先停住，终稿决定续播还是真打断。停住期间不发真帧、不开新句；
 * 开播后的停住、句间、上游断流都按节拍发静音帧，设备播放时间轴不断。
 *
 * 播放器按真实墙钟节拍（60ms/帧）跑，断言一律用与时长无关的不变式：
 * 先轮询等到某个计数涨到位，再断言另一个计数没动、或帧的先后次序对，
 * 不写「多少毫秒内应该发出几帧」这类随机器负载浮动的上下界。
 * 帧的到达节奏由测试自己往 Sinks 里投喂控制，不依赖 sleep 卡时间点。
 */
class ScheduledPlayerPauseTest {

    private static final long AWAIT_TIMEOUT_MS = 5000;

    /** 与 ScheduledPlayer.SENTENCE_GAP_FRAMES 一致：句间要补的静音帧数 */
    private static final int SENTENCE_GAP_FRAMES = 4;

    private MessageSender sender;
    private ScheduledPlayer player;

    /** 下发的帧序列，true=真帧，false=静音帧 */
    private final List<Boolean> sentFrames = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        sender = mock(MessageSender.class);
        doAnswer(inv -> {
            byte[] frame = inv.getArgument(1);
            sentFrames.add(!Arrays.equals(frame, OpusProcessor.silenceFrame()));
            return null;
        }).when(sender).sendBinaryMessage(any(), any(), anyLong());
        // WebSocketSession(String) 不给底层 spring session 赋值，getSessionId() 会 NPE，只能用真连接构造
        org.springframework.web.socket.WebSocketSession springSession =
                mock(org.springframework.web.socket.WebSocketSession.class);
        lenient().when(springSession.getId()).thenReturn("s1");
        player = new ScheduledPlayer(new WebSocketSession(springSession), sender);
    }

    @AfterEach
    void tearDown() {
        player.stop();
    }

    @Test
    void pauseBeforePlayDefersStartUntilResume() throws InterruptedException {
        player.pause(5000);
        player.play(flux(speeches(5, "第一句。")), true);

        // 暂停期间一帧都不下发，也不发 tts start
        verify(sender, after(300).never()).sendBinaryMessage(any(), any(), anyLong());
        verify(sender, never()).sendTtsMessage(any(), isNull(), eq("start"));

        player.resume();

        awaitAtLeast(this::realFrames, 5);
        verify(sender).sendTtsMessage(any(), isNull(), eq("start"));
    }

    @Test
    void pauseMidPlaybackHoldsFramesAndFillsSilenceThenResumes() throws InterruptedException {
        Sinks.Many<Speech> sink = Sinks.many().unicast().onBackpressureBuffer();
        player.play(sink.asFlux(), true);
        emit(sink, speeches(2, "第一句。"));
        awaitAtLeast(this::realFrames, 2);

        player.pause(5000);
        assertThat(player.isPaused()).isTrue();
        // 等暂停后的第一帧静音出现，此后同一条发送线程不可能再发真帧
        awaitAtLeast(this::silenceFrames, silenceFrames() + 1);
        int heldRealFrames = realFrames();

        // 暂停期间到达的帧只入队不下发
        emit(sink, speeches(3, null));
        awaitAtLeast(this::silenceFrames, silenceFrames() + SENTENCE_GAP_FRAMES);
        assertThat(realFrames()).isEqualTo(heldRealFrames);
        assertThat(player.hasContent()).isTrue();

        player.resume();

        awaitAtLeast(this::realFrames, 5);
        sink.tryEmitComplete();
        verify(sender, timeout(3000)).sendTtsMessage(any(), isNull(), eq("stop"));
    }

    @Test
    void pauseAutoResumesAfterDeadline() throws InterruptedException {
        player.pause(300);
        player.play(flux(speeches(3, "第一句。")), true);

        awaitAtLeast(this::realFrames, 3);
        assertThat(player.isPaused()).isFalse();
    }

    @Test
    void stopWhilePausedReleasesSenderThread() throws InterruptedException {
        player.pause(5000);
        player.play(flux(speeches(3, "第一句。")), true);
        // 开播前暂停时发送线程阻塞在 pauseLock 上，stop 必须把它放出来
        Thread senderThread = (Thread) ReflectionTestUtils.getField(player, "senderThread");
        assertThat(senderThread).isNotNull();

        player.stop();

        awaitUntil(() -> !senderThread.isAlive());
        assertThat(realFrames()).isZero();
        assertThat(silenceFrames()).isZero();
        assertThat(player.isPaused()).isFalse();
        assertThat(player.hasContent()).isFalse();
    }

    @Test
    void pauseDuringSentenceGapHoldsNextSentence() throws InterruptedException {
        Sinks.Many<Speech> first = Sinks.many().unicast().onBackpressureBuffer();
        player.play(first.asFlux(), true);
        player.play(flux(speeches(3, "第二句。")), true);
        emit(first, speeches(3, "第一句。"));
        awaitAtLeast(this::realFrames, 3);

        player.pause(5000);
        awaitAtLeast(this::silenceFrames, silenceFrames() + 1);
        // 第一句收尾后第二句才被订阅入队，暂停期间不许开新句
        first.tryEmitComplete();
        awaitAtLeast(this::silenceFrames, silenceFrames() + SENTENCE_GAP_FRAMES);

        assertThat(realFrames()).isEqualTo(3);
        verify(sender, never()).sendTtsMessage(any(), eq("第二句。"), eq("sentence_start"));

        player.resume();

        verify(sender, timeout(3000)).sendTtsMessage(any(), eq("第二句。"), eq("sentence_start"));
        awaitAtLeast(this::realFrames, 6);
        verify(sender, timeout(3000)).sendTtsMessage(any(), isNull(), eq("stop"));
    }

    @Test
    void sentenceGapIsFilledWithSilenceAndTrailingGapIsSkipped() {
        // 第一句用 Sinks 投喂，保证第二句是排队进来的，不会因为第一句先收尾而提前收场
        Sinks.Many<Speech> first = Sinks.many().unicast().onBackpressureBuffer();
        player.play(first.asFlux(), true);
        player.play(flux(speeches(3, "第二句。")), true);
        emit(first, speeches(3, "第一句。"));
        first.tryEmitComplete();

        verify(sender, timeout(5000)).sendTtsMessage(any(), isNull(), eq("stop"));

        List<Boolean> timeline = frameTimeline();
        assertThat(realFrames()).isEqualTo(6);
        assertThat(silenceAfterRealFrame(timeline, 3)).isGreaterThanOrEqualTo(SENTENCE_GAP_FRAMES);
        // 末句之后的句间静音不播，最后下发的必须是真帧
        assertThat(timeline.get(timeline.size() - 1)).isTrue();
    }

    @Test
    void upstreamStallIsFilledWithSilence() {
        Flux<Speech> stalled = Flux.concat(
                flux(speeches(2, "第一句。")),
                Mono.delay(Duration.ofMillis(400)).thenMany(flux(speeches(2, null))));
        player.play(stalled, true);

        verify(sender, timeout(5000)).sendTtsMessage(any(), isNull(), eq("stop"));

        List<Boolean> timeline = frameTimeline();
        assertThat(realFrames()).isEqualTo(4);
        // 400ms 断流至少跨 3 个 60ms 节拍，每拍都得有静音顶上
        assertThat(silenceAfterRealFrame(timeline, 2)).isGreaterThanOrEqualTo(3);
    }

    private static List<Speech> speeches(int count, String text) {
        List<Speech> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte[] data = {(byte) i};
            list.add(i == 0 && text != null ? Speech.ofOpus(data, text) : Speech.ofOpus(data));
        }
        return list;
    }

    private static Flux<Speech> flux(List<Speech> speeches) {
        return Flux.fromIterable(speeches);
    }

    private static void emit(Sinks.Many<Speech> sink, List<Speech> speeches) {
        speeches.forEach(speech -> sink.emitNext(speech,
                Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1))));
    }

    private int realFrames() {
        return countFrames(true);
    }

    private int silenceFrames() {
        return countFrames(false);
    }

    private int countFrames(boolean real) {
        int count = 0;
        for (Boolean frame : frameTimeline()) {
            if (frame == real) {
                count++;
            }
        }
        return count;
    }

    private List<Boolean> frameTimeline() {
        synchronized (sentFrames) {
            return List.copyOf(sentFrames);
        }
    }

    /** 第 nth 个真帧与下一个真帧之间夹了多少静音帧 */
    private static int silenceAfterRealFrame(List<Boolean> timeline, int nth) {
        int real = 0;
        int silence = 0;
        for (Boolean frame : timeline) {
            if (frame) {
                real++;
                if (real > nth) {
                    break;
                }
            } else if (real == nth) {
                silence++;
            }
        }
        return silence;
    }

    private static void awaitAtLeast(IntSupplier counter, int expected) throws InterruptedException {
        awaitUntil(() -> counter.getAsInt() >= expected);
        assertThat(counter.getAsInt()).isGreaterThanOrEqualTo(expected);
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}

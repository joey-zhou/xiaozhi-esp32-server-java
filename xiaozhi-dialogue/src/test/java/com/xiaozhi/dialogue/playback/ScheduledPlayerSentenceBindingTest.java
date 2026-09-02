package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.OpusProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
 * PCM 流的句子文本与 Opus 帧的归属绑定：字幕必须跟着自己那句的音频一起下发。
 *
 * TTS 分块与 60ms 帧长不对齐，一句话的尾巴常常残留在编码器内部缓冲里。
 * 新句到达时若不先把残留 flush 成独立帧，本句文本就会绑到混着上一句尾音的帧上
 * （线上表现为字幕比音频提前、末句字幕丢失）。
 * 断言只看事件顺序（句子文本 / 真帧），静音帧与时刻不参与断言。
 */
class ScheduledPlayerSentenceBindingTest {

    /** 一个 Opus 帧的样本数（60ms @16kHz） */
    private static final int FRAME_SAMPLES = AudioUtils.FRAME_SIZE;

    private MessageSender sender;
    private ScheduledPlayer player;

    /** 下发事件流水：真帧、静音帧、句子文本，按发生顺序记录 */
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        sender = mock(MessageSender.class);
        doAnswer(inv -> {
            byte[] frame = inv.getArgument(1);
            events.add(Arrays.equals(frame, OpusProcessor.silenceFrame()) ? "silence" : "frame");
            return null;
        }).when(sender).sendBinaryMessage(any(), any(), anyLong());
        doAnswer(inv -> {
            if ("sentence_start".equals(inv.getArgument(2))) {
                events.add("text:" + inv.<String>getArgument(1));
            }
            return null;
        }).when(sender).sendTtsMessage(any(), any(), any());
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
    void sentenceBoundaryFlushesPreviousTailBeforeNewText() {
        // 第一句 1.5 帧：编码出 1 帧，半帧残留在编码器里
        player.play(Flux.just(
                new Speech(pcm(FRAME_SAMPLES + FRAME_SAMPLES / 2), "第一句。"),
                new Speech(pcm(FRAME_SAMPLES), "第二句。")), true);

        verify(sender, timeout(5000)).sendTtsMessage(any(), isNull(), eq("stop"));

        // 残留的半帧先单独成帧归上一句，第二句的文本才落到自己的首帧上
        assertThat(audioTimeline())
                .containsExactly("text:第一句。", "frame", "frame", "text:第二句。", "frame");
    }

    @Test
    void textHeldWhenFirstPcmChunkShorterThanOneFrame() {
        // 首块 PCM 不足一帧，编码器一帧都吐不出来，文本必须暂存而不是丢弃
        player.play(Flux.just(
                new Speech(pcm(FRAME_SAMPLES / 2), "你好呀。"),
                new Speech(pcm(FRAME_SAMPLES / 2))), true);

        verify(sender, timeout(5000)).sendTtsMessage(any(), isNull(), eq("stop"));

        assertThat(audioTimeline()).containsExactly("text:你好呀。", "frame");
    }

    @Test
    void pendingTextIsAttachedToFlushedTailOnComplete() {
        // 整句 PCM 都不足一帧，只有流结束时的 flush 能把它变成帧，暂存的文本要补绑上去
        player.play(Flux.just(new Speech(pcm(FRAME_SAMPLES / 2), "就这样。")), true);

        verify(sender, timeout(5000)).sendTtsMessage(any(), isNull(), eq("stop"));

        assertThat(audioTimeline()).containsExactly("text:就这样。", "frame");
    }

    @Test
    void carriedTextIsBoundToPreviousSentenceTailFrame() {
        // 上一句凑不满一帧就来了新句：上一句的字幕补绑到它自己的收尾帧上，不能整句丢字幕
        player.play(Flux.just(
                new Speech(pcm(FRAME_SAMPLES / 2), "第一句。"),
                new Speech(pcm(FRAME_SAMPLES), "第二句。")), true);

        verify(sender, timeout(5000)).sendTtsMessage(any(), isNull(), eq("stop"));

        assertThat(audioTimeline())
                .containsExactly("text:第一句。", "frame", "text:第二句。", "frame");
    }

    @Test
    void lateFrameAfterStopIsDiscarded() throws InterruptedException {
        LateCallbackPublisher upstream = new LateCallbackPublisher();
        player.play(Flux.from(upstream), true);
        assertThat(upstream.awaitSubscribed()).isTrue();

        player.stop();
        // 打断后上一轮 TTS 回调线程慢一拍仍在推残帧，代次已变，残帧不许进队列
        upstream.emit(new Speech(pcm(FRAME_SAMPLES), "上一轮残句。"));

        assertThat(player.isDrained()).isTrue();
        assertThat(player.hasContent()).isFalse();
        assertThat(events).isEmpty();
        verify(sender, never()).sendTtsMessage(any(), eq("上一轮残句。"), eq("sentence_start"));
    }

    // 打断时编码器里未成帧的残留必须丢弃，否则下一轮首帧会带上被打断那句的尾音
    @Test
    void stopDiscardsEncoderLeftoverSoNextTurnStartsClean() {
        OpusProcessor encoder = (OpusProcessor) ReflectionTestUtils.getField(player, "opusProcessor");
        // 1.5 帧：编码出 1 帧，半帧留在编码器里
        player.play(Flux.just(new Speech(pcm(FRAME_SAMPLES + FRAME_SAMPLES / 2), "被打断的一句。")), true);
        OpusProcessor.LeftoverState state =
                (OpusProcessor.LeftoverState) ReflectionTestUtils.getField(encoder, "leftoverStates");
        awaitUntil(() -> state.leftoverCount > 0);

        player.stop();

        assertThat(state.leftoverCount).isZero();
        assertThat(state.leftoverBuffer).containsOnly((short) 0);
    }

    /** 轮询等待条件成立，禁止用固定时长 sleep 做同步 */
    private static void awaitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    /** 下发事件流水，去掉与时刻相关的静音帧 */
    private List<String> audioTimeline() {
        synchronized (events) {
            return events.stream().filter(event -> !"silence".equals(event)).toList();
        }
    }

    /** 生成 samples 个样本的 16bit 小端非静音 PCM，编码结果必然不等于静音帧 */
    private static byte[] pcm(int samples) {
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            short value = (short) (8000 * Math.sin(2 * Math.PI * 440 * i / AudioUtils.SAMPLE_RATE));
            pcm[i * 2] = (byte) (value & 0xFF);
            pcm[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return pcm;
    }

    /**
     * 直接持有下游 Subscriber 的音频源：取消订阅后仍能推送，
     * 用来复现"上一轮 TTS 回调线程比 stop() 慢一拍"的时序。
     */
    private static final class LateCallbackPublisher implements Publisher<Speech> {

        private final AtomicReference<Subscriber<? super Speech>> subscriber = new AtomicReference<>();
        private final CountDownLatch subscribed = new CountDownLatch(1);

        @Override
        public void subscribe(Subscriber<? super Speech> downstream) {
            subscriber.set(downstream);
            downstream.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
            subscribed.countDown();
        }

        boolean awaitSubscribed() throws InterruptedException {
            return subscribed.await(5, TimeUnit.SECONDS);
        }

        void emit(Speech speech) {
            subscriber.get().onNext(speech);
        }
    }
}

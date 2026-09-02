package com.xiaozhi.communication.protocol;

import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 不出网的 STT 假体，是驱动打断 / 误打断 / 多轮对话的唯一确定性手段（真实 provider 全走网络）。
 *
 * <p>行为：订阅本轮音频流并按顺序记录收到的每一帧；每收到第 N 帧就回调预置的第 N 条中间结果；
 * 音频流 onComplete 后返回预置的终稿。
 *
 * <p>编排入口：{@link #withPartials} 设中间结果、{@link #withFinalText} / {@link #withFinalResult}
 * 设终稿、{@link #hangUntilReleased()} 让本轮终稿迟迟不返回（模拟 STT 卡住，用于过期结果丢弃的用例）。
 *
 * <p>盲区：真实 provider 的分段、标点、热词、断流重连全部不模拟。
 */
class ScriptedSttService implements SttService {

    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(5);

    private final List<byte[]> receivedFrames = new CopyOnWriteArrayList<>();
    private final AtomicInteger streamCalls = new AtomicInteger();
    private final AtomicInteger completedStreams = new AtomicInteger();

    private volatile List<String> partials = List.of();
    private volatile SttResult finalResult = SttResult.textOnly("");
    private volatile CountDownLatch releaseGate;

    @Override
    public String getProviderName() {
        return "scripted";
    }

    @Override
    public SttResult stream(Flux<byte[]> audioSink) {
        return stream(audioSink, text -> {
        });
    }

    @Override
    public SttResult stream(Flux<byte[]> audioSink, Consumer<String> onPartialText) {
        int turn = streamCalls.incrementAndGet();
        List<String> scriptedPartials = partials;
        AtomicInteger frameIndex = new AtomicInteger();
        try {
            audioSink.doOnNext(frame -> {
                receivedFrames.add(frame);
                int index = frameIndex.getAndIncrement();
                if (index < scriptedPartials.size()) {
                    onPartialText.accept(scriptedPartials.get(index));
                }
            }).collectList().block(STREAM_TIMEOUT);
        } catch (RuntimeException e) {
            // 流未在上限内结束时按空结果返回，避免把测试线程挂死
            completedStreams.incrementAndGet();
            return SttResult.textOnly("");
        }
        CountDownLatch gate = releaseGate;
        if (gate != null) {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return SttResult.textOnly("");
            }
        }
        completedStreams.incrementAndGet();
        return finalResult;
    }

    // ========== 编排 ==========

    ScriptedSttService withPartials(String... texts) {
        this.partials = List.of(texts);
        return this;
    }

    ScriptedSttService withFinalText(String text) {
        this.finalResult = SttResult.textOnly(text);
        return this;
    }

    ScriptedSttService withFinalResult(SttResult result) {
        this.finalResult = result;
        return this;
    }

    /** 让识别在音频流结束后继续挂起，直到 {@link #release()}。返回自身便于链式编排 */
    ScriptedSttService hangUntilReleased() {
        this.releaseGate = new CountDownLatch(1);
        return this;
    }

    void release() {
        CountDownLatch gate = releaseGate;
        if (gate != null) {
            gate.countDown();
        }
    }

    // ========== 断言入口 ==========

    /** 进过 STT 的音频帧，按到达顺序 */
    List<byte[]> receivedFrames() {
        return List.copyOf(receivedFrames);
    }

    /** stream 被调用的次数，等于本次会话开启过的识别轮数 */
    int streamCalls() {
        return streamCalls.get();
    }

    /** 已返回终稿的识别轮数 */
    int completedStreams() {
        return completedStreams.get();
    }

    void clearFrames() {
        receivedFrames.clear();
    }
}

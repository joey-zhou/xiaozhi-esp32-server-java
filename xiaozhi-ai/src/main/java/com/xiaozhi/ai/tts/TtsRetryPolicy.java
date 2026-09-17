package com.xiaozhi.ai.tts;

import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TTS 合成流的重试策略。
 */
public final class TtsRetryPolicy {

    private TtsRetryPolicy() {
    }

    /**
     * 只在一帧音频都没产出时才重试。
     * <p>
     * 合成流是逐帧下发音频的，已经 onNext 出去的帧无法撤回。若在中途失败时重新订阅，
     * 整句会从头再合成一遍，用户听到重复片段，重复音频还会被下游写进音频缓存永久留存。
     *
     * @param source     合成流，必须是冷流（重试会重新订阅）
     * @param maxRetries 最大重试次数
     * @param delayMs    重试间隔（毫秒）
     */
    public static <T> Flux<T> retryIfNothingEmitted(Flux<T> source, long maxRetries, long delayMs) {
        return Flux.defer(() -> {
            AtomicBoolean emitted = new AtomicBoolean(false);
            return source
                    .doOnNext(item -> emitted.set(true))
                    .retryWhen(Retry.fixedDelay(maxRetries, Duration.ofMillis(delayMs))
                            .filter(error -> !emitted.get()));
        });
    }
}

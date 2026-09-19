package com.xiaozhi.ai.stt.providers;

import com.xiaozhi.ai.stt.SttResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 整句离线识别的收音与结果口径：本轮所有帧拼成一段送解码、空音频不解码、模型没加载按本地错误。
 * 真解码依赖模型文件，这里用子类把解码换成记账，SenseVoice 标签到情绪取值的映射单独钉。
 */
class SenseVoiceSttServiceTest {

    @Test
    void concatenatesEveryFrameBeforeDecoding() throws Exception {
        RecordingDecoder service = new RecordingDecoder(SttResult.withEmotion("你好", "happy", null));
        Sinks.Many<byte[]> sink = Sinks.many().multicast().onBackpressureBuffer();
        List<String> partials = new ArrayList<>();

        CompletableFuture<SttResult> result = CompletableFuture.supplyAsync(
                () -> service.stream(sink.asFlux(), partials::add));
        sink.tryEmitNext(new byte[]{1, 2});
        sink.tryEmitNext(new byte[]{3, 4});
        sink.tryEmitNext(new byte[]{5, 6});
        sink.tryEmitComplete();

        assertThat(result.get(3, TimeUnit.SECONDS).text()).isEqualTo("你好");
        assertThat(result.get().emotion()).isEqualTo("happy");
        assertThat(service.decoded).containsExactly(new byte[]{1, 2, 3, 4, 5, 6});
        // 整句识别没有中间文本
        assertThat(partials).isEmpty();
    }

    @Test
    void emptyStreamReturnsEmptyTextWithoutDecoding() {
        RecordingDecoder service = new RecordingDecoder(SttResult.textOnly("不该被调用"));

        SttResult result = service.stream(Flux.empty());

        assertThat(result.text()).isEmpty();
        assertThat(result.operationFailed()).isFalse();
        assertThat(service.decoded).isEmpty();
    }

    @Test
    void streamErrorStillDecodesWhatArrived() throws Exception {
        RecordingDecoder service = new RecordingDecoder(SttResult.textOnly("半句"));
        Sinks.Many<byte[]> sink = Sinks.many().multicast().onBackpressureBuffer();

        CompletableFuture<SttResult> result = CompletableFuture.supplyAsync(() -> service.stream(sink.asFlux()));
        sink.tryEmitNext(new byte[]{9, 9});
        sink.tryEmitError(new IllegalStateException("upstream closed"));

        assertThat(result.get(3, TimeUnit.SECONDS).text()).isEqualTo("半句");
        assertThat(service.decoded).containsExactly(new byte[]{9, 9});
    }

    @Test
    void unloadedModelIsLocalError() {
        SenseVoiceSttService service = new SenseVoiceSttService("/nonexistent/sense-voice", 2);

        SttResult result = service.stream(Flux.just(new byte[]{1, 2}));

        assertThat(result.failureReason()).isEqualTo(SttResult.FAILURE_LOCAL_ERROR);
    }

    @Test
    void initializeFailsClearlyWhenModelDirectoryMissing() {
        SenseVoiceSttService service = new SenseVoiceSttService("/nonexistent/sense-voice", 2);

        org.assertj.core.api.Assertions.assertThatThrownBy(service::initialize)
                .hasMessageContaining("model directory not found");
        assertThat(service.isModelLoaded()).isFalse();
    }

    @Test
    void senseVoiceTagsMapToLowercaseValues() {
        assertThat(SenseVoiceSttService.normalizeTag("<|HAPPY|>")).isEqualTo("happy");
        assertThat(SenseVoiceSttService.normalizeTag("<|NEUTRAL|>")).isEqualTo("neutral");
        assertThat(SenseVoiceSttService.normalizeTag("<|Speech|>")).isEqualTo("speech");
        assertThat(SenseVoiceSttService.normalizeTag("SAD")).isEqualTo("sad");
        assertThat(SenseVoiceSttService.normalizeTag("<||>")).isNull();
        assertThat(SenseVoiceSttService.normalizeTag("  ")).isNull();
        assertThat(SenseVoiceSttService.normalizeTag(null)).isNull();
    }

    /** 不加载模型，把解码换成记账 */
    private static final class RecordingDecoder extends SenseVoiceSttService {
        final List<byte[]> decoded = new ArrayList<>();
        private final SttResult canned;

        RecordingDecoder(SttResult canned) {
            super("/nonexistent/sense-voice", 1);
            this.canned = canned;
        }

        @Override
        public boolean isModelLoaded() {
            return true;
        }

        @Override
        SttResult decode(byte[] pcm) {
            decoded.add(pcm);
            return canned;
        }
    }

    // 解码路数 × 单路线程数就是同时占用的核数，不得超过预算。
    // 之前路数写死核数/2、线程数默认 2，乘出来是整机核数，超了一倍
    @Test
    void decodeConcurrencyTimesThreadsStaysWithinCoreBudget() {
        for (int budget : new int[]{1, 2, 4, 8, 16}) {
            for (int numThreads : new int[]{1, 2, 3, 4}) {
                int concurrency = SenseVoiceSttService.decodeConcurrency(budget, numThreads);

                assertThat(concurrency).isGreaterThanOrEqualTo(1);
                // 预算连一路都放不下时也得留一路，否则本地识别直接不可用；其余情况不得超预算
                if (budget >= numThreads) {
                    assertThat(concurrency * numThreads).isLessThanOrEqualTo(budget);
                }
            }
        }
    }

    // 线程数是配置项，调大它并发路数必须跟着减，不能只改一头
    @Test
    void raisingThreadsLowersConcurrency() {
        assertThat(SenseVoiceSttService.decodeConcurrency(8, 1)).isEqualTo(8);
        assertThat(SenseVoiceSttService.decodeConcurrency(8, 2)).isEqualTo(4);
        assertThat(SenseVoiceSttService.decodeConcurrency(8, 4)).isEqualTo(2);
    }
}

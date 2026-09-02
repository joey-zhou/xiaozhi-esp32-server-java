package com.xiaozhi.ai.tts;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 打断时 dispose 的是分句后的流，取消必须一路传到 LLM 流，
 * 否则 LLM 在后台继续跑完，token 白烧，完成回调还会把整段回复写进历史。
 */
class SentenceHelperCancelTest {

    @Test
    void disposingConvertedFluxCancelsUpstream() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Flux<String> llmStream = Flux.<String>never().doOnCancel(() -> cancelled.set(true));

        Disposable subscription = new SentenceHelper().convert(llmStream).subscribe();
        subscription.dispose();

        assertThat(cancelled).isTrue();
    }

    @Test
    void completedUpstreamStillFlushesTail() {
        List<SentenceHelper.SentenceResult> sentences = new SentenceHelper()
                .convert(Flux.just("你好呀今天", "天气真不错"))
                .collectList().block();

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).text()).isEqualTo("你好呀今天天气真不错");
    }
}

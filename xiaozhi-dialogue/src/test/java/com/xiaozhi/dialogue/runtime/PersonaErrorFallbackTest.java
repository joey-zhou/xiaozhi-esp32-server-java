package com.xiaozhi.dialogue.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 报错时合成器订阅没有 error consumer，用户那边是彻底静默。
 * 一个字都没说出来就失败时要补一句口播。
 */
class PersonaErrorFallbackTest {

    @Test
    void failureBeforeAnyOutputSpeaksFallback() {
        List<String> spoken = Persona.withErrorFallback(Flux.error(new IllegalStateException("LLM 挂了")))
                .collectList().block();

        assertThat(spoken).hasSize(1);
        assertThat(spoken.get(0)).isIn(fallbackMessages());
    }

    @Test
    void failureAfterSpeakingDoesNotAppendApology() {
        List<String> spoken = Persona.withErrorFallback(
                        Flux.just("今天天气", "不错").concatWith(Flux.error(new IllegalStateException("中途断了"))))
                .collectList().block();

        assertThat(spoken).containsExactly("今天天气", "不错");
    }

    @Test
    void blankOutputStillCountsAsSilent() {
        List<String> spoken = Persona.withErrorFallback(
                        Flux.just("", "   ").concatWith(Flux.error(new IllegalStateException("空转后失败"))))
                .collectList().block();

        // 只吐了空白等于没开口，仍要兜底
        assertThat(spoken).hasSize(3);
        assertThat(spoken.get(2)).isIn(fallbackMessages());
    }

    @Test
    void successfulStreamIsUntouched() {
        List<String> spoken = Persona.withErrorFallback(Flux.just("你好", "呀")).collectList().block();

        assertThat(spoken).containsExactly("你好", "呀");
    }

    /** 兜底话术是 Persona 的私有常量，反射取出来对照，防止异常原文被当成回复播给用户 */
    @SuppressWarnings("unchecked")
    private static List<String> fallbackMessages() {
        return (List<String>) ReflectionTestUtils.getField(Persona.class, "ERROR_FALLBACK_MESSAGES");
    }
}

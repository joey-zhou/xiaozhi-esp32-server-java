package com.xiaozhi.ai.stt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住 STT 结果的失败通道：识别失败与「用户没说话」必须能区分开，
 * 且失败标记不得吞掉已识别到的文本与情感。
 */
class SttResultFailureTest {

    @Test
    void failureResultIsRecognizedAsFailed() {
        SttResult result = SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR);

        assertThat(result.operationFailed()).isTrue();
        assertThat(result.failureReason()).isEqualTo(SttResult.FAILURE_UPSTREAM_ERROR);
        assertThat(result.text()).isEmpty();
    }

    @Test
    void emptyTextWithoutFailureIsNotFailed() {
        SttResult result = SttResult.textOnly("");

        assertThat(result.operationFailed()).isFalse();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void withFailureKeepsRecognizedTextAndEmotion() {
        SttResult result = emotionalResult().withFailure(SttResult.FAILURE_TIMEOUT);

        assertThat(result.operationFailed()).isTrue();
        assertThat(result.failureReason()).isEqualTo(SttResult.FAILURE_TIMEOUT);
        assertThat(result.text()).isEqualTo("今天天气不错");
        assertThat(result.emotion()).isEqualTo("happy");
        assertThat(result.emotionScore()).isEqualTo(0.8);
        assertThat(result.emotionDegree()).isEqualTo("strong");
        assertThat(result.hasEmotion()).isTrue();
    }

    @Test
    void withFailureOnNullReasonKeepsResultSuccessful() {
        SttResult source = emotionalResult();

        SttResult result = source.withFailure(null);

        assertThat(result).isSameAs(source);
        assertThat(result.operationFailed()).isFalse();
    }

    @Test
    void textOnlyAndEmotionFactoriesProduceSuccessfulResults() {
        assertThat(SttResult.textOnly("你好").operationFailed()).isFalse();
        assertThat(SttResult.withEmotion("你好", "sad", 0.5).operationFailed()).isFalse();
        assertThat(emotionalResult().operationFailed()).isFalse();
    }

    // 等待超时收尾：一个字都没识别出来才算失败，否则「连接正常但没识别出文本」的提示会把排查引向模型能力
    @Test
    void withFailureIfEmptyMarksTimeoutOnlyWhenNothingWasRecognized() {
        assertThat(SttResult.textOnly("").withFailureIfEmpty(SttResult.FAILURE_TIMEOUT).failureReason())
                .isEqualTo(SttResult.FAILURE_TIMEOUT);
        assertThat(SttResult.textOnly("已识别的半句").withFailureIfEmpty(SttResult.FAILURE_TIMEOUT).operationFailed())
                .isFalse();
    }

    // 已有更具体的失败原因时不能被超时短码覆盖
    @Test
    void withFailureIfEmptyKeepsTheExistingReason() {
        SttResult upstreamFailure = SttResult.failure(SttResult.FAILURE_UPSTREAM_ERROR);

        assertThat(upstreamFailure.withFailureIfEmpty(SttResult.FAILURE_TIMEOUT))
                .isSameAs(upstreamFailure);
    }

    private static SttResult emotionalResult() {
        return SttResult.withFullEmotion("今天天气不错", "happy", 0.8, "strong", 0.6);
    }
}

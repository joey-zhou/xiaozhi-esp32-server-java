package com.xiaozhi.ai.stt.providers;

import com.xiaozhi.ai.stt.SttResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住讯飞识别的失败收尾：服务端返回错误码后必须立刻结束等待并记下失败原因，
 * 不能把本轮拖到识别超时才返回。
 */
class XfyunSttServiceFailureTest {

    @Test
    void failRecordsReasonAndEndsWait() {
        AtomicReference<String> failureReason = new AtomicReference<>();
        AtomicBoolean latchReleased = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        XfyunSttService.fail(failureReason, SttResult.FAILURE_UPSTREAM_ERROR, latchReleased, latch);

        assertThat(failureReason.get()).isEqualTo(SttResult.FAILURE_UPSTREAM_ERROR);
        assertThat(latchReleased.get()).isTrue();
        assertThat(latch.getCount()).isZero();
    }

    @Test
    void failOnlyReleasesWaitOnce() {
        AtomicReference<String> failureReason = new AtomicReference<>();
        AtomicBoolean latchReleased = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(2);

        XfyunSttService.fail(failureReason, SttResult.FAILURE_UPSTREAM_ERROR, latchReleased, latch);
        XfyunSttService.fail(failureReason, SttResult.FAILURE_TIMEOUT, latchReleased, latch);

        assertThat(latch.getCount()).isEqualTo(1);
        assertThat(failureReason.get()).isEqualTo(SttResult.FAILURE_TIMEOUT);
    }
}

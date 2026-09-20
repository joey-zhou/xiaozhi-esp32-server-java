package com.xiaozhi.ai.tts.providers;

import com.xiaozhi.ai.tts.TtsOverloadException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 钉住本地合成的并发准入：合成是纯 CPU 推理，不限并发时 N 句同时合成会开出 N×numThreads 条
 * native 线程抢核，RTF 随之线性恶化，越过 1 就合成慢于播放、句间必然出现停顿。
 * 因此排队满与等待超时都必须放弃本句并计数，不能退化成无界等待。
 */
class SherpaOnnxTtsServiceAdmissionTest {

    private static final long TEST_WAIT_TIMEOUT_MS = 200;
    private static final long AWAIT_TIMEOUT_MS = 3000;

    private ThreadPoolExecutor pool;
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void restoreSharedExecutor() {
        release.countDown();
        if (pool != null) {
            pool.shutdownNow();
        }
        ReflectionTestUtils.setField(SherpaOnnxTtsService.class, "synthExecutor", null);
        ReflectionTestUtils.setField(SherpaOnnxTtsService.class, "waitTimeoutMs", 8000L);
    }

    @Test
    void submitWithAdmissionReturnsTaskResultWhenCapacityAvailable() throws Exception {
        installPool(1, 4);

        assertThat(SherpaOnnxTtsService.submitWithAdmission(() -> "音频")).isEqualTo("音频");
    }

    @Test
    void submitWithAdmissionPropagatesTaskFailureUnchanged() {
        installPool(1, 4);

        assertThatThrownBy(() -> SherpaOnnxTtsService.submitWithAdmission(() -> {
            throw new IllegalStateException("模型未加载");
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("模型未加载");
    }

    @Test
    void submitWithAdmissionRejectsWhenQueueIsFull() throws Exception {
        installPool(1, 1);
        long droppedBefore = droppedCount();
        occupyWorker();
        pool.execute(this::awaitRelease);

        assertThatThrownBy(() -> SherpaOnnxTtsService.submitWithAdmission(() -> "音频"))
            .isInstanceOf(TtsOverloadException.class)
            .hasMessageContaining("排队已满");
        assertThat(droppedCount()).isEqualTo(droppedBefore + 1);
    }

    @Test
    void submitWithAdmissionGivesUpWhenWaitExceedsTimeout() throws Exception {
        installPool(1, 4);
        long droppedBefore = droppedCount();
        occupyWorker();

        assertThatThrownBy(() -> SherpaOnnxTtsService.submitWithAdmission(() -> "音频"))
            .isInstanceOf(TtsOverloadException.class)
            .hasMessageContaining("排队超过");
        assertThat(droppedCount()).isEqualTo(droppedBefore + 1);
    }

    /** 等超时后放弃的那次合成不能再被执行，否则白占一条工作线程 */
    @Test
    void submitWithAdmissionCancelsQueuedTaskAfterTimeout() throws Exception {
        installPool(1, 4);
        AtomicBoolean executed = new AtomicBoolean();
        occupyWorker();

        assertThatThrownBy(() -> SherpaOnnxTtsService.submitWithAdmission(() -> {
            executed.set(true);
            return "音频";
        })).isInstanceOf(TtsOverloadException.class);

        // 放行占位任务后再排一个探针：单线程 FIFO，探针跑完说明被放弃的那个已经没机会了
        release.countDown();
        CountDownLatch probe = new CountDownLatch(1);
        pool.execute(probe::countDown);
        assertThat(probe.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(executed).isFalse();
    }

    // 并发跟着核预算收放，队列的物理容量按最大并发留足；实际排队上限要随当前并发走，
    // 否则并发收缩后一条工作线程背着几十句的队列，句句都要等满超时才被放弃
    @Test
    void queueLimitFollowsCurrentConcurrencyNotPhysicalCapacity() throws Exception {
        installPool(1, 64);
        occupyWorker();
        for (int i = 0; i < 8; i++) {
            pool.execute(this::awaitRelease);
        }

        assertThatThrownBy(() -> SherpaOnnxTtsService.submitWithAdmission(() -> "音频"))
            .isInstanceOf(TtsOverloadException.class)
            .hasMessageContaining("排队已满");
    }

    @Test
    void queueLimitGrowsWithConcurrency() throws Exception {
        installPool(2, 64);
        occupyWorker();
        occupyWorker();
        for (int i = 0; i < 8; i++) {
            pool.execute(this::awaitRelease);
        }

        // 两路并发的排队上限是 16，第 9 句进得了队，只是等不到工作线程
        assertThatThrownBy(() -> SherpaOnnxTtsService.submitWithAdmission(() -> "音频"))
            .isInstanceOf(TtsOverloadException.class)
            .hasMessageContaining("排队超过");
    }

    private void installPool(int threads, int queueCapacity) {
        pool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                rejectionHandler());
        ReflectionTestUtils.setField(SherpaOnnxTtsService.class, "synthExecutor", pool);
        ReflectionTestUtils.setField(SherpaOnnxTtsService.class, "waitTimeoutMs", TEST_WAIT_TIMEOUT_MS);
    }

    /** 占满工作线程并等它真的跑起来，避免任务还在队列里就往下断言 */
    private void occupyWorker() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        pool.execute(() -> {
            started.countDown();
            awaitRelease();
        });
        assertThat(started.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    }

    private void awaitRelease() {
        try {
            release.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static RejectedExecutionHandler rejectionHandler() {
        return (RejectedExecutionHandler) ReflectionTestUtils.getField(SherpaOnnxTtsService.class, "REJECTION_HANDLER");
    }

    private static long droppedCount() {
        return ((AtomicLong) ReflectionTestUtils.getField(SherpaOnnxTtsService.class, "droppedCount")).get();
    }
}

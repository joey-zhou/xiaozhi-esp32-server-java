package com.xiaozhi.common.monitoring;

import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountingRejectionHandlerTest {

    @Test
    void rejectedExecutionIncrementsCountAndDelegates() {
        CountingRejectionHandler handler = new CountingRejectionHandler(new ThreadPoolExecutor.AbortPolicy());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS, new SynchronousQueue<>());
        try {
            Runnable task = () -> { };

            assertThatThrownBy(() -> handler.rejectedExecution(task, executor))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(handler.rejectedCount()).isEqualTo(1);

            assertThatThrownBy(() -> handler.rejectedExecution(task, executor))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(handler.rejectedCount()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectedExecutionPropagatesDelegateExceptionEvenWhenDelegateThrowsDifferentType() {
        CountingRejectionHandler handler = new CountingRejectionHandler((task, executor) -> {
            throw new IllegalStateException("自定义拒绝策略");
        });
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.SECONDS, new SynchronousQueue<>());
        try {
            Runnable task = () -> { };

            assertThatThrownBy(() -> handler.rejectedExecution(task, executor))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(handler.rejectedCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}

package com.xiaozhi.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedLocksTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private DistributedLocks locks;

    private final AtomicInteger runs = new AtomicInteger();

    @BeforeEach
    void setUp() {
        locks = new DistributedLocks(redissonClient);
        when(redissonClient.getLock("xiaozhi:lock:task:demo")).thenReturn(lock);
    }

    @Test
    void runsAndReleasesWhenTheLockIsFree() throws InterruptedException {
        when(lock.tryLock(0, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThat(locks.runIfFree("task:demo", runs::incrementAndGet)).isTrue();

        assertThat(runs).hasValue(1);
        verify(lock).unlock();
    }

    @Test
    void skipsWhenAnotherInstanceHoldsTheLock() throws InterruptedException {
        when(lock.tryLock(0, TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThat(locks.runIfFree("task:demo", runs::incrementAndGet)).isFalse();

        assertThat(runs).hasValue(0);
        verify(lock, never()).unlock();
    }

    @Test
    void releasesEvenWhenTheTaskFails() throws InterruptedException {
        when(lock.tryLock(0, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() -> locks.runIfFree("task:demo", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        verify(lock).unlock();
    }

    @Test
    void interruptedWaitKeepsTheInterruptFlag() throws InterruptedException {
        when(lock.tryLock(0, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException());

        assertThat(locks.runIfFree("task:demo", runs::incrementAndGet)).isFalse();

        assertThat(Thread.interrupted()).isTrue();
        assertThat(runs).hasValue(0);
    }

    @Test
    void callWithinWaitsThenRunsAndReleases() throws InterruptedException {
        when(lock.tryLock(3000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThat(locks.callWithin("task:demo", Duration.ofSeconds(3), () -> "done", () -> "busy")).isEqualTo("done");

        verify(lock).unlock();
    }

    @Test
    void callWithinFallsBackWhenTheLockStaysBusy() throws InterruptedException {
        when(lock.tryLock(3000, TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThat(locks.callWithin("task:demo", Duration.ofSeconds(3), () -> "done", () -> "busy")).isEqualTo("busy");

        verify(lock, never()).unlock();
    }
}

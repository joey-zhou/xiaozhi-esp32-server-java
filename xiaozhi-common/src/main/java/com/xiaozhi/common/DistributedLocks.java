package com.xiaozhi.common;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 跨进程互斥的唯一入口，锁名统一加 {@link #LOCK_PREFIX}，调用方只给业务名。
 * 不指定租期，由 Redisson 看门狗随持锁线程存活续期，进程崩溃后锁自动过期。
 */
@Slf4j
@Component
public class DistributedLocks {

    private static final String LOCK_PREFIX = "xiaozhi:lock:";

    private final RedissonClient redissonClient;

    public DistributedLocks(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 抢到锁就执行，抢不到直接跳过（定时任务不需要排队补跑）。
     *
     * @return 本实例是否执行了 task
     */
    public boolean runIfFree(String lockName, Runnable task) {
        return callWithin(lockName, Duration.ZERO, () -> {
            task.run();
            return true;
        }, () -> {
            log.debug("已有实例在执行，跳过本轮: {}", lockName);
            return false;
        });
    }

    /**
     * 等锁至多 wait，拿到就执行 task；等不到或等锁被中断时执行 whenBusy，它可以给降级结果，也可以抛出
     */
    public <T> T callWithin(String lockName, Duration wait, Supplier<T> task, Supplier<T> whenBusy) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockName);
        boolean locked;
        try {
            locked = lock.tryLock(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            locked = false;
        }
        if (!locked) {
            return whenBusy.get();
        }
        try {
            return task.get();
        } finally {
            // 看门狗续期失败时锁已自动释放，再 unlock 会抛异常盖掉业务结果
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

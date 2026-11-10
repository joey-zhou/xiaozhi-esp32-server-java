package com.xiaozhi.common;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 跨进程互斥。server 与 dialogue 都会触发同一个定时任务，多实例部署时同一时刻还会有多份，
 * 动 DB、Redis、对象存储的任务要靠它只让一个实例执行。
 */
@Slf4j
@Component
public class DistributedLocks {

    private final RedissonClient redissonClient;

    public DistributedLocks(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 抢到锁就执行，抢不到直接跳过（定时任务不需要排队补跑）。
     * 不指定租期，由 Redisson 看门狗随线程存活续期，进程崩溃后锁自动过期。
     *
     * @return 本实例是否执行了 task
     */
    public boolean runIfFree(String lockName, Runnable task) {
        RLock lock = redissonClient.getLock(lockName);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.SECONDS);
            if (!locked) {
                log.debug("已有实例在执行，跳过本轮: {}", lockName);
                return false;
            }
            task.run();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待任务锁被中断: {}", lockName);
            return false;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

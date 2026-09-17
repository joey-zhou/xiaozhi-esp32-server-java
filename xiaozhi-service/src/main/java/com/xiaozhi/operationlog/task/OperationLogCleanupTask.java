package com.xiaozhi.operationlog.task;

import com.xiaozhi.operationlog.service.OperationLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 操作日志清理任务：sys_operation_log 只增不删，长期运行会无限堆积，凌晨3点半批量清理过期行。
 * com.xiaozhi.task 同时在 server 与 dialogue 两个入口的 @ComponentScan 清单里，必须整体持有分布式锁。
 */
@Slf4j
@Component
public class OperationLogCleanupTask {

    private static final int BATCH_SIZE = 500;
    private static final String CLEANUP_LOCK = "lock:task:operation-log-cleanup";

    @Value("${xiaozhi.operation-log.retention-days:180}")
    private int retentionDays;

    @Resource
    private OperationLogService operationLogService;

    @Resource
    private RedissonClient redissonClient;

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanupExpiredLogs() {
        runExclusively(CLEANUP_LOCK, () -> {
            log.info("========== 开始清理过期操作日志（保留{}天）==========", retentionDays);
            int deleted = operationLogService.deleteExpired(retentionDays, BATCH_SIZE);
            log.info("========== 过期操作日志清理完成，共{}条 ==========", deleted);
        });
    }

    /**
     * 抢到锁的实例执行 task，抢不到的直接跳过本轮（定时任务不需要排队补跑）。
     * 不指定租期，由 Redisson 看门狗随线程存活续期，进程崩溃后锁自动过期。
     */
    private void runExclusively(String lockName, Runnable task) {
        RLock lock = redissonClient.getLock(lockName);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.SECONDS);
            if (!locked) {
                log.info("已有实例在执行，跳过本轮: {}", lockName);
                return;
            }
            task.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待任务锁被中断: {}", lockName);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

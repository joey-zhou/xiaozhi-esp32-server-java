package com.xiaozhi.task;

import com.xiaozhi.verifycode.service.VerifyCodeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 验证码清理任务：sys_code 只增不删，设备码/邮箱码过期后不再被任何查询命中，
 * 长期运行会无限堆积（未鉴权的 OTA 探测尤其容易灌爆），凌晨3点批量清理过期行。
 * <p>
 * 保留期比 10 分钟的有效期宽出一天，避免误删刚好卡在边界、还在被排查的记录。
 * com.xiaozhi.task 同时在 server 与 dialogue 两个入口的 @ComponentScan 清单里，必须整体持有分布式锁。
 */
@Slf4j
@Component
public class VerifyCodeCleanupTask {

    private static final int RETENTION_MINUTES = 24 * 60;
    private static final int BATCH_SIZE = 500;
    private static final String CLEANUP_LOCK = "lock:task:verifycode-cleanup";

    @Resource
    private VerifyCodeService verifyCodeService;

    @Resource
    private RedissonClient redissonClient;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredCodes() {
        runExclusively(CLEANUP_LOCK, () -> {
            log.info("========== 开始清理过期验证码 ==========");
            int deleted = verifyCodeService.deleteExpired(RETENTION_MINUTES, BATCH_SIZE);
            log.info("========== 过期验证码清理完成，共{}条 ==========", deleted);
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

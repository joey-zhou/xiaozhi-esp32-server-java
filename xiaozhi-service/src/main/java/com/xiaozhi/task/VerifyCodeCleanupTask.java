package com.xiaozhi.task;

import com.xiaozhi.common.DistributedLocks;
import com.xiaozhi.verifycode.service.VerifyCodeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    private DistributedLocks distributedLocks;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredCodes() {
        distributedLocks.runIfFree(CLEANUP_LOCK, () -> {
            log.info("========== 开始清理过期验证码 ==========");
            int deleted = verifyCodeService.deleteExpired(RETENTION_MINUTES, BATCH_SIZE);
            log.info("========== 过期验证码清理完成，共{}条 ==========", deleted);
        });
    }

}

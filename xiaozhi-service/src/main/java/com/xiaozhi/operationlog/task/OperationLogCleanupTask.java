package com.xiaozhi.operationlog.task;

import com.xiaozhi.common.DistributedLocks;
import com.xiaozhi.operationlog.service.OperationLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    private DistributedLocks distributedLocks;

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanupExpiredLogs() {
        distributedLocks.runIfFree(CLEANUP_LOCK, () -> {
            log.info("========== 开始清理过期操作日志（保留{}天）==========", retentionDays);
            int deleted = operationLogService.deleteExpired(retentionDays, BATCH_SIZE);
            log.info("========== 过期操作日志清理完成，共{}条 ==========", deleted);
        });
    }

}

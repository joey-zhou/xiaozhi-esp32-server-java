package com.xiaozhi.operationlog.task;

import com.xiaozhi.operationlog.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * com.xiaozhi.task 同时挂在 server 与 dialogue 两个入口的 @ComponentScan 清单里，
 * 多实例部署时同一秒点会有多个进程触发，抢不到分布式锁的实例必须整轮跳过。
 */
@ExtendWith(MockitoExtension.class)
class OperationLogCleanupTaskTest {

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private final OperationLogCleanupTask task = new OperationLogCleanupTask();

    @Test
    void cleanupExpiredLogsSkipsEntirelyWhenLockIsHeldElsewhere() throws InterruptedException {
        ReflectionTestUtils.setField(task, "operationLogService", operationLogService);
        ReflectionTestUtils.setField(task, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(task, "retentionDays", 180);
        when(redissonClient.getLock("lock:task:operation-log-cleanup")).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(false);

        task.cleanupExpiredLogs();

        verifyNoInteractions(operationLogService);
        verify(lock, never()).unlock();
    }

    @Test
    void cleanupExpiredLogsUsesConfiguredRetentionAndReleasesLock() throws InterruptedException {
        ReflectionTestUtils.setField(task, "operationLogService", operationLogService);
        ReflectionTestUtils.setField(task, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(task, "retentionDays", 90);
        when(redissonClient.getLock("lock:task:operation-log-cleanup")).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        task.cleanupExpiredLogs();

        verify(operationLogService).deleteExpired(90, 500);
        verify(lock).unlock();
    }
}

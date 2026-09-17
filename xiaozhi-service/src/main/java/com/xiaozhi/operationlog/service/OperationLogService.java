package com.xiaozhi.operationlog.service;

import com.xiaozhi.common.model.bo.OperationLogBO;

public interface OperationLogService {

    /**
     * 异步保存操作日志，不影响主流程性能。
     */
    void saveAsync(OperationLogBO log);

    /**
     * 删除超过保留期的操作日志，分批删除避免长事务锁表。
     *
     * @return 实际删除的行数
     */
    int deleteExpired(int retentionDays, int batchSize);
}

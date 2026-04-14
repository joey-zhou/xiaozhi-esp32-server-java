package com.xiaozhi.operationlog.service;

import com.xiaozhi.common.model.bo.OperationLogBO;

public interface OperationLogService {

    /**
     * 异步保存操作日志，不影响主流程性能。
     */
    void saveAsync(OperationLogBO log);
}

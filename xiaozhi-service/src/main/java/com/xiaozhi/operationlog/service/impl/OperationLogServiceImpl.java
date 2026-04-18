package com.xiaozhi.operationlog.service.impl;

import com.xiaozhi.common.model.bo.OperationLogBO;
import com.xiaozhi.operationlog.convert.OperationLogConvert;
import com.xiaozhi.operationlog.dal.mysql.mapper.OperationLogMapper;
import com.xiaozhi.operationlog.service.OperationLogService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Resource
    private OperationLogMapper operationLogMapper;

    @Resource
    private OperationLogConvert operationLogConvert;

    @Override
    @Async
    public void saveAsync(OperationLogBO operationLog) {
        try {
            operationLogMapper.insert(operationLogConvert.toDO(operationLog));
        } catch (Exception e) {
            log.error("保存操作日志失败: module={} operation={}", operationLog.getModule(), operationLog.getOperation(), e);
        }
    }
}

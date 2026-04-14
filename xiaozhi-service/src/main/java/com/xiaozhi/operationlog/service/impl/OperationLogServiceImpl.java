package com.xiaozhi.operationlog.service.impl;

import com.xiaozhi.common.model.bo.OperationLogBO;
import com.xiaozhi.operationlog.convert.OperationLogConvert;
import com.xiaozhi.operationlog.dal.mysql.mapper.OperationLogMapper;
import com.xiaozhi.operationlog.service.OperationLogService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OperationLogServiceImpl implements OperationLogService {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogServiceImpl.class);

    @Resource
    private OperationLogMapper operationLogMapper;

    @Resource
    private OperationLogConvert operationLogConvert;

    @Override
    @Async
    public void saveAsync(OperationLogBO log) {
        try {
            operationLogMapper.insert(operationLogConvert.toDO(log));
        } catch (Exception e) {
            logger.error("保存操作日志失败: module={} operation={}", log.getModule(), log.getOperation(), e);
        }
    }
}

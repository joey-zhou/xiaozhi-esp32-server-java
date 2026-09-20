package com.xiaozhi.operationlog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaozhi.common.model.bo.OperationLogBO;
import com.xiaozhi.operationlog.convert.OperationLogConvert;
import com.xiaozhi.operationlog.dal.mysql.dataobject.OperationLogDO;
import com.xiaozhi.operationlog.dal.mysql.mapper.OperationLogMapper;
import com.xiaozhi.operationlog.service.OperationLogService;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

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

    @Override
    public int deleteExpired(int retentionDays, int batchSize) {
        LocalDateTime expireBefore = DateUtils.now().minusDays(retentionDays);
        int deleted = 0;
        while (true) {
            List<Long> ids = operationLogMapper.selectList(new LambdaQueryWrapper<OperationLogDO>()
                    .select(OperationLogDO::getId)
                    .lt(OperationLogDO::getCreateTime, expireBefore)
                    .orderByAsc(OperationLogDO::getId)
                    .last("LIMIT " + batchSize))
                .stream()
                .map(OperationLogDO::getId)
                .toList();
            if (ids.isEmpty()) {
                break;
            }
            operationLogMapper.delete(new LambdaQueryWrapper<OperationLogDO>().in(OperationLogDO::getId, ids));
            deleted += ids.size();
        }
        return deleted;
    }
}

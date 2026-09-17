package com.xiaozhi.summary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.summary.convert.SummaryConvert;
import com.xiaozhi.summary.dal.mysql.dataobject.SummaryDO;
import com.xiaozhi.summary.dal.mysql.mapper.SummaryMapper;
import com.xiaozhi.summary.service.SummaryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

@Slf4j
@Service
public class SummaryServiceImpl implements SummaryService {

    @Resource
    private SummaryMapper summaryMapper;

    @Resource
    private SummaryConvert summaryConvert;

    @Override
    public PageResult<SummaryBO> page(String deviceId, Integer userId, Integer roleId, int pageNo, int pageSize) {
        IPage<SummaryBO> result = summaryMapper.selectPage(new Page<>(pageNo, pageSize), deviceId, userId, roleId);
        return new PageResult<>(result.getRecords(), result.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional
    public int deleteBySessionIds(Collection<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }
        return summaryMapper.delete(new LambdaQueryWrapper<SummaryDO>()
            .in(SummaryDO::getSessionId, sessionIds));
    }

    @Override
    @Transactional
    public int delete(Integer roleId, String deviceId, Long summaryId) {
        if (roleId == null || !StringUtils.hasText(deviceId)) {
            return 0;
        }

        LambdaQueryWrapper<SummaryDO> queryWrapper = new LambdaQueryWrapper<SummaryDO>()
            .eq(SummaryDO::getRoleId, roleId)
            .eq(SummaryDO::getDeviceId, deviceId);
        if (summaryId != null) {
            queryWrapper.eq(SummaryDO::getCreateTime, toLocalDateTime(Instant.ofEpochMilli(summaryId)));
        }
        return summaryMapper.delete(queryWrapper);
    }

    @Override
    @Transactional
    public void save(SummaryBO summary) {
        if (summary == null || !StringUtils.hasText(summary.getDeviceId()) || summary.getRoleId() == null
            || summary.getLastMessageTimestamp() == null) {
            return;
        }

        SummaryDO summaryDO = summaryConvert.toDO(summary);
        if (summaryDO.getPromptTokens() == null) {
            summaryDO.setPromptTokens(0);
        }
        if (summaryDO.getCompletionTokens() == null) {
            summaryDO.setCompletionTokens(0);
        }
        if (summaryDO.getCreateTime() == null) {
            summaryDO.setCreateTime(LocalDateTime.now());
        }
        try {
            summaryMapper.insert(summaryDO);
        } catch (DuplicateKeyException e) {
            // 主键是设备、角色加批次最后一条消息的时间：同一段历史被两个对话实例各压缩了一次
            // （两个标签页续接同一会话、重连时上一实例还在收尾），先到的那份已经落库，这份不必再存，也不算失败
            log.info("摘要已由另一个对话实例保存，跳过: deviceId={}, roleId={}, lastMessageTimestamp={}",
                summaryDO.getDeviceId(), summaryDO.getRoleId(), summaryDO.getLastMessageTimestamp());
        }
    }

    @Override
    @Transactional
    public int deleteByDeviceId(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return 0;
        }
        return summaryMapper.delete(new LambdaQueryWrapper<SummaryDO>()
            .eq(SummaryDO::getDeviceId, deviceId));
    }

    @Override
    public SummaryBO findLast(String deviceId, Integer roleId) {
        if (!StringUtils.hasText(deviceId) || roleId == null) {
            return null;
        }

        SummaryDO summaryDO = summaryMapper.selectOne(new LambdaQueryWrapper<SummaryDO>()
            .eq(SummaryDO::getDeviceId, deviceId)
            .eq(SummaryDO::getRoleId, roleId)
            .orderByDesc(SummaryDO::getCreateTime)
            .last("LIMIT 1"));
        return summaryConvert.toBO(summaryDO);
    }

    @Override
    public SummaryBO findLastBySession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        SummaryDO summaryDO = summaryMapper.selectOne(new LambdaQueryWrapper<SummaryDO>()
            .eq(SummaryDO::getSessionId, sessionId)
            .orderByDesc(SummaryDO::getCreateTime)
            .last("LIMIT 1"));
        return summaryConvert.toBO(summaryDO);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MILLIS), ZoneId.systemDefault());
    }
}

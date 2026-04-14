package com.xiaozhi.summary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.summary.convert.SummaryConvert;
import com.xiaozhi.summary.dal.mysql.dataobject.SummaryDO;
import com.xiaozhi.summary.dal.mysql.mapper.SummaryMapper;
import com.xiaozhi.summary.service.SummaryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SummaryServiceImpl implements SummaryService {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Resource
    private SummaryMapper summaryMapper;

    @Resource
    private SummaryConvert summaryConvert;

    @Override
    public PageResp<SummaryBO> page(String deviceId, Integer roleId, Integer pageNo, Integer pageSize) {
        int currentPage = pageNo == null || pageNo < 1 ? DEFAULT_PAGE_NO : pageNo;
        int currentSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        if (!StringUtils.hasText(deviceId) || roleId == null) {
            return new PageResp<>(List.of(), 0L, currentPage, currentSize);
        }

        Page<SummaryDO> page = new Page<>(currentPage, currentSize);
        IPage<SummaryDO> result = summaryMapper.selectPage(page, new LambdaQueryWrapper<SummaryDO>()
            .eq(SummaryDO::getDeviceId, deviceId)
            .eq(SummaryDO::getRoleId, roleId)
            .orderByDesc(SummaryDO::getCreateTime));
        return new PageResp<>(
            summaryConvert.toBOList(result.getRecords()),
            result.getTotal(),
            Math.toIntExact(result.getCurrent()),
            Math.toIntExact(result.getSize())
        );
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
        summaryMapper.insert(summaryDO);
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

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MILLIS), ZoneId.systemDefault());
    }
}

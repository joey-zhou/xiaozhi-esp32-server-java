package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.SummaryMapper;
import com.xiaozhi.entity.SysSummary;
import com.xiaozhi.service.SysSummaryService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SysSummaryServiceImpl extends BaseServiceImpl implements SysSummaryService {

    @Resource
    private SummaryMapper summaryMapper;

    /**
     * 查询摘要记录
     *
     * @param summary
     * @param pageFilter
     * @return
     */
    @Override
    public List<SysSummary> query(SysSummary summary, PageFilter pageFilter) {
        if(pageFilter != null){
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return summaryMapper.findSummary(summary);
    }

    /**
     * 删除摘要记录
     *
     * @return
     */
    @Override
    @Transactional
    public int delete(@Positive int roleId, @NotNull String deviceId, Long summaryId ) {
        // 当前的设计， summaryId，实际是用创建时间的毫秒数代替，对外暴露的必须是一个long型的id才符合语义
        java.time.Instant createTime = null;
        if (summaryId != null) {
            createTime = java.time.Instant.ofEpochMilli(summaryId);
        }
        return summaryMapper.deleteSummary( roleId, deviceId, createTime);
    }
}
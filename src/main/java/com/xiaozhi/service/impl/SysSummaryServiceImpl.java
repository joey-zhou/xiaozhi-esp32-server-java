package com.xiaozhi.service.impl;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysSummary;
import com.xiaozhi.repository.SysSummaryRepository;
import com.xiaozhi.service.SysSummaryService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SysSummaryServiceImpl extends BaseServiceImpl implements SysSummaryService {

    @Resource
    private SysSummaryRepository sysSummaryRepository;

    /**
     * 查询摘要记录
     *
     * @param summary
     * @param pageFilter
     * @return
     */
    @Override
    public List<SysSummary> query(SysSummary summary, PageFilter pageFilter) {
        if (pageFilter != null) {
            Page<SysSummary> page = sysSummaryRepository.findSummary(
                    summary.getDeviceId(),
                    summary.getRoleId(),
                    PageRequest.of(pageFilter.getStart() - 1, pageFilter.getLimit(), Sort.by(Sort.Direction.DESC, "createTime"))
            );
            return page.getContent();
        }
        return sysSummaryRepository.findSummary(
                summary.getDeviceId(),
                summary.getRoleId(),
                PageRequest.of(0, 10)
        ).getContent();
    }

    /**
     * 删除摘要记录
     *
     * @return
     */
    @Override
    @Transactional
    public int delete(@Positive int roleId, @NotNull String deviceId, Long summaryId ) {
        // 当前的设计，summaryId，实际是用创建时间的毫秒数代替，对外暴露的必须是一个 long 型的 id 才符合语义
        java.time.Instant createTime = null;
        if (summaryId != null) {
            createTime = java.time.Instant.ofEpochMilli(summaryId);
        }
        return sysSummaryRepository.deleteSummary(roleId, deviceId, createTime);
    }
}

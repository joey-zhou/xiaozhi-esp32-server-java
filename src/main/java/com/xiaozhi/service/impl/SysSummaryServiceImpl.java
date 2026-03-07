package com.xiaozhi.service.impl;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysSummary;
import com.xiaozhi.repository.SysSummaryRepository;
import com.xiaozhi.service.SysSummaryService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SysSummaryServiceImpl extends BaseServiceImpl implements SysSummaryService {

    @Resource
    private SysSummaryRepository summaryRepository;

    @Override
    public List<SysSummary> query(SysSummary summary, PageFilter pageFilter) {
        if (pageFilter != null) {
            return summaryRepository.findSummary(
                    summary.getDeviceId(),
                    summary.getRoleId(),
                    org.springframework.data.domain.PageRequest.of(pageFilter.getStart() - 1, pageFilter.getLimit())
            ).getContent();
        }
        return summaryRepository.findSummary(summary);
    }

    @Override
    @Transactional
    public int delete(@Positive int roleId, @NotNull String deviceId, Long summaryId) {
        Instant createTime = null;
        if (summaryId != null) {
            createTime = Instant.ofEpochMilli(summaryId);
        }
        return summaryRepository.deleteSummary(roleId, deviceId, createTime);
    }
}

package com.xiaozhi.dao;

import com.xiaozhi.entity.SysSummary;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public interface SummaryMapper {
    void saveSummary(SysSummary summary);
    SysSummary findLastSummary(String deviceId, int roleId);
    // 根据deviceId和roleId查询
    List<SysSummary> findSummary(SysSummary  summary);

    int deleteSummary(@Positive int roleId, @NotNull String deviceId,java.time.Instant  createTime);
}

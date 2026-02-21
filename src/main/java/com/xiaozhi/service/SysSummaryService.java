package com.xiaozhi.service;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysSummary;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import javax.annotation.Nullable;
import java.util.List;

public interface SysSummaryService {
    List<SysSummary> query(SysSummary message, PageFilter pageFilter);
    int delete(@Positive int roleId, @NotNull String deviceId,  @Nullable Long summaryId );
}

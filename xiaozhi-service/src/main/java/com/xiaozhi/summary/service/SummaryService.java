package com.xiaozhi.summary.service;

import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.common.model.resp.PageResp;

public interface SummaryService {

    PageResp<SummaryBO> page(String deviceId, Integer roleId, Integer pageNo, Integer pageSize);

    int delete(Integer roleId, String deviceId, Long summaryId);

    void save(SummaryBO summary);

    SummaryBO findLast(String deviceId, Integer roleId);
}

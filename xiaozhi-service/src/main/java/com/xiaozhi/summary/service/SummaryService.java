package com.xiaozhi.summary.service;

import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.common.model.PageResult;

public interface SummaryService {

    PageResult<SummaryBO> page(String deviceId, Integer roleId, Integer pageNo, Integer pageSize);

    int delete(Integer roleId, String deviceId, Long summaryId);

    /** 删除该设备名下全部摘要，不限角色；设备被删除时用于清理关联数据。 */
    int deleteByDeviceId(String deviceId);

    void save(SummaryBO summary);

    SummaryBO findLast(String deviceId, Integer roleId);
}

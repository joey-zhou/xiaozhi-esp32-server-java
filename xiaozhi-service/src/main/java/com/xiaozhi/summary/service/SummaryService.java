package com.xiaozhi.summary.service;

import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.common.model.PageResult;

import java.util.Collection;

public interface SummaryService {

    /**
     * 摘要分页，新的在前，每条带设备名与角色名。
     *
     * @param deviceId 指定设备只查这一台，为空时查 userId 名下全部设备
     * @param roleId   为空时不限角色
     */
    PageResult<SummaryBO> page(String deviceId, Integer userId, Integer roleId, int pageNo, int pageSize);

    int delete(Integer roleId, String deviceId, Long summaryId);

    /** 删除该设备名下全部摘要，不限角色；设备被删除时用于清理关联数据。 */
    int deleteByDeviceId(String deviceId);

    /** 删除该角色下全部摘要，不限设备（含网页会话）；角色被删除时用于清理关联数据。 */
    int deleteByRoleId(Integer roleId);

    /** 删除这些 Web 会话的全部摘要 */
    int deleteBySessionIds(Collection<String> sessionIds);

    void save(SummaryBO summary);

    SummaryBO findLast(String deviceId, Integer roleId);

    /** Web 会话最近一次摘要；与按设备查的不做重载，漏写 roleId 时不会悄悄查错 */
    SummaryBO findLastBySession(String sessionId);
}

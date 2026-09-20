package com.xiaozhi.summary.convert;

import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.common.model.resp.SummaryResp;
import com.xiaozhi.summary.dal.mysql.dataobject.SummaryDO;
import com.xiaozhi.utils.DateUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public interface SummaryConvert {

    /** 设备名与角色名只在分页 SQL 里关联出来，单表读不带 */
    @Mapping(target = "deviceName", ignore = true)
    @Mapping(target = "roleName", ignore = true)
    SummaryBO toBO(SummaryDO summaryDO);

    SummaryDO toDO(SummaryBO summaryBO);

    SummaryResp toResp(SummaryBO summaryBO);

    default Instant map(LocalDateTime value) {
        return DateUtils.toInstant(value);
    }

    default LocalDateTime map(Instant value) {
        return DateUtils.toDateTime(value);
    }
}

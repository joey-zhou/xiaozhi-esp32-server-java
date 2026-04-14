package com.xiaozhi.summary.convert;

import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.summary.dal.mysql.dataobject.SummaryDO;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SummaryConvert {

    SummaryBO toBO(SummaryDO summaryDO);

    List<SummaryBO> toBOList(List<SummaryDO> summaryDOList);

    SummaryDO toDO(SummaryBO summaryBO);

    default Instant map(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    default LocalDateTime map(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }
}

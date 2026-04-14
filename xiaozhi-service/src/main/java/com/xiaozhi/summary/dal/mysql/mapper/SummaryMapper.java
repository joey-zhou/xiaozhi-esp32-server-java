package com.xiaozhi.summary.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaozhi.summary.dal.mysql.dataobject.SummaryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SummaryMapper extends BaseMapper<SummaryDO> {
}

package com.xiaozhi.operationlog.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaozhi.operationlog.dal.mysql.dataobject.SysOperationLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<SysOperationLogDO> {
}

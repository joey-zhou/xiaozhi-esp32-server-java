package com.xiaozhi.operationlog.convert;

import com.xiaozhi.common.model.bo.OperationLogBO;
import com.xiaozhi.operationlog.dal.mysql.dataobject.SysOperationLogDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OperationLogConvert {

    SysOperationLogDO toDO(OperationLogBO bo);
}

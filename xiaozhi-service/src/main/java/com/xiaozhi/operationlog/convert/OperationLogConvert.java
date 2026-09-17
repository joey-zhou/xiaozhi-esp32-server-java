package com.xiaozhi.operationlog.convert;

import com.xiaozhi.common.model.bo.OperationLogBO;
import com.xiaozhi.operationlog.dal.mysql.dataobject.OperationLogDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OperationLogConvert {

    OperationLogDO toDO(OperationLogBO bo);
}

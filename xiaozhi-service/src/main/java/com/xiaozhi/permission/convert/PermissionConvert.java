package com.xiaozhi.permission.convert;

import com.xiaozhi.common.model.bo.PermissionBO;
import com.xiaozhi.common.model.resp.PermissionTreeResp;
import com.xiaozhi.permission.dal.mysql.dataobject.PermissionDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PermissionConvert {

    @Mapping(target = "children", ignore = true)
    PermissionBO toBO(PermissionDO permissionDO);

    PermissionTreeResp toTreeResp(PermissionBO permission);
}

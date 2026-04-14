package com.xiaozhi.permission.convert;

import com.xiaozhi.common.model.resp.PermissionResp;
import com.xiaozhi.common.model.resp.PermissionTreeResp;
import com.xiaozhi.permission.dal.mysql.dataobject.PermissionDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PermissionConvert {

    PermissionResp toResp(PermissionDO permissionDO);

    PermissionTreeResp toTreeResp(PermissionDO permissionDO);

    PermissionTreeResp toTreeResp(PermissionResp permissionResp);
}

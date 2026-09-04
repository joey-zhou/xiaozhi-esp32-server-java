package com.xiaozhi.authrole.convert;

import com.xiaozhi.authrole.dal.mysql.dataobject.AuthRoleDO;
import com.xiaozhi.common.model.bo.AuthRoleBO;
import com.xiaozhi.common.model.resp.AuthRolePermissionConfigResp;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthRoleConvert {

    AuthRoleBO toBO(AuthRoleDO authRoleDO);

    AuthRoleResp toResp(AuthRoleBO authRole);

    @Mapping(target = "permissionTree", ignore = true)
    @Mapping(target = "checkedPermissionIds", ignore = true)
    AuthRolePermissionConfigResp toPermissionConfigResp(AuthRoleBO authRole);
}

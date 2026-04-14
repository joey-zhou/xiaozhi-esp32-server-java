package com.xiaozhi.authrole.convert;

import com.xiaozhi.authrole.dal.mysql.dataobject.AuthRoleDO;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuthRoleConvert {

    AuthRoleResp toResp(AuthRoleDO authRoleDO);
}

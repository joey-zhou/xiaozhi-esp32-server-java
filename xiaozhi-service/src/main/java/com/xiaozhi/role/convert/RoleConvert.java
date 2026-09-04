package com.xiaozhi.role.convert;

import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.resp.RoleResp;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.role.model.RoleProjection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleConvert {

    RoleResp toResp(RoleProjection projection);

    @Mapping(target = "totalDevice", ignore = true)
    @Mapping(target = "modelName", ignore = true)
    @Mapping(target = "modelProvider", ignore = true)
    @Mapping(target = "ttsProvider", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    RoleResp toResp(RoleBO roleBO);

    @Mapping(target = "ttsPitch", source = "ttsPitch", defaultValue = "1.0")
    @Mapping(target = "ttsSpeed", source = "ttsSpeed", defaultValue = "1.0")
    @Mapping(target = "temperature", source = "temperature", defaultValue = "0.7d")
    @Mapping(target = "topP", source = "topP", defaultValue = "0.9d")
    @Mapping(target = "inactiveTimeoutSeconds", source = "inactiveTimeoutSeconds", defaultValue = "60")
    RoleBO toBO(RoleDO roleDO);

    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    RoleDO copy(RoleDO roleDO);

}

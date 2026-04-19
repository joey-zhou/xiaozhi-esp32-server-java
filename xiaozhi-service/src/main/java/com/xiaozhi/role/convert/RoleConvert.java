package com.xiaozhi.role.convert;

import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.req.RoleCreateReq;
import com.xiaozhi.common.model.req.RoleUpdateReq;
import com.xiaozhi.common.model.resp.RoleResp;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface RoleConvert {

    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    RoleDO toDO(RoleCreateReq req);

    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDO(RoleUpdateReq req, @MappingTarget RoleDO roleDO);

    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    RoleBO toCreateBO(RoleCreateReq req);

    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    RoleBO toUpdateBO(RoleUpdateReq req);

    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    RoleDO toCreateDO(RoleBO bo);

    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDO(RoleBO bo, @MappingTarget RoleDO roleDO);

    RoleResp toResp(RoleDO roleDO);

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
    RoleBO toBO(RoleDO roleDO);

    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    RoleDO copy(RoleDO roleDO);

}

package com.xiaozhi.user.convert;

import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.req.UserRegisterReq;
import com.xiaozhi.common.model.req.UserUpdateReq;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.user.dal.mysql.dataobject.UserDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserConvert {

    UserBO toBO(UserDO userDO);

    UserResp toResp(UserDO userDO);

    UserResp toResp(UserBO userBO);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    UserDO toDO(UserBO userBO);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "authRoleId", ignore = true)
    @Mapping(target = "isAdmin", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "wxOpenId", ignore = true)
    @Mapping(target = "wxUnionId", ignore = true)
    @Mapping(target = "loginIp", ignore = true)
    @Mapping(target = "loginTime", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateDO(UserUpdateReq req, @MappingTarget UserDO userDO);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateDO(UserBO userBO, @MappingTarget UserDO userDO);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "authRoleId", ignore = true)
    @Mapping(target = "isAdmin", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "wxOpenId", ignore = true)
    @Mapping(target = "wxUnionId", ignore = true)
    @Mapping(target = "loginIp", ignore = true)
    @Mapping(target = "loginTime", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    UserBO toBO(UserRegisterReq req);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "authRoleId", ignore = true)
    @Mapping(target = "isAdmin", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "wxOpenId", ignore = true)
    @Mapping(target = "wxUnionId", ignore = true)
    @Mapping(target = "loginIp", ignore = true)
    @Mapping(target = "loginTime", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateBO(UserUpdateReq req, @MappingTarget UserBO userBO);
}

package com.xiaozhi.device.convert;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.req.DeviceUpdateReq;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface DeviceConvert {

    DeviceBO toBO(DeviceDO deviceDO);

    DeviceResp toResp(VerifyCodeBO codeBO);

    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    DeviceDO toDO(DeviceBO deviceBO);

    @Mapping(target = "deviceId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "ip", ignore = true)
    @Mapping(target = "wifiName", ignore = true)
    @Mapping(target = "chipModelName", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDO(DeviceUpdateReq req, @MappingTarget DeviceDO deviceDO);

    @Mapping(target = "deviceId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "ip", ignore = true)
    @Mapping(target = "wifiName", ignore = true)
    @Mapping(target = "chipModelName", ignore = true)
    @Mapping(target = "sessionId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateBO(DeviceUpdateReq req, @MappingTarget DeviceBO deviceBO);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateDO(DeviceBO deviceBO, @MappingTarget DeviceDO deviceDO);
}

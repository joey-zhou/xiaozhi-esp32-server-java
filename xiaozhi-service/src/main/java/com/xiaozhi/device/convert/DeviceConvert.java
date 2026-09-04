package com.xiaozhi.device.convert;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.model.DeviceProjection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DeviceConvert {

    @Mapping(target = "sessionId", ignore = true)
    @Mapping(target = "roleName", ignore = true)
    DeviceBO toBO(DeviceDO deviceDO);

    @Mapping(target = "deviceName", ignore = true)
    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "roleName", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "totalMessage", ignore = true)
    @Mapping(target = "wifiName", ignore = true)
    @Mapping(target = "ip", ignore = true)
    @Mapping(target = "chipModelName", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "mcpList", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    DeviceResp toResp(VerifyCodeBO codeBO);

    @Mapping(target = "sessionId", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "audioPath", ignore = true)
    @Mapping(target = "mcpList", ignore = true)
    DeviceResp toResp(DeviceProjection projection);
}

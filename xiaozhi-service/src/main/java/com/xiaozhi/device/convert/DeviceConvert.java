package com.xiaozhi.device.convert;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.model.DeviceProjection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DeviceConvert {

    /** sessionId/roleName 是 dialogue 运行期字段，DO 里没有对应列，转换后恒为 null */
    @Mapping(target = "sessionId", ignore = true)
    @Mapping(target = "roleName", ignore = true)
    DeviceBO toBO(DeviceDO deviceDO);

    @Mapping(target = "deviceName", ignore = true)
    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "roleName", ignore = true)
    @Mapping(target = "state", ignore = true)
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

    /**
     * 写路径出参：设备字段取自刚落库的聚合根，角色名由调用方把手上已有的传进来
     * （聚合根上只有 roleId，roleName 是分页 SQL 才 JOIN 出来的列）。
     * <p>sessionId / code / audioPath / mcpList 不属于这几个写接口的返回内容，保持留空。
     */
    @Mapping(target = "roleName", source = "roleName")
    @Mapping(target = "sessionId", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "audioPath", ignore = true)
    @Mapping(target = "mcpList", ignore = true)
    DeviceResp toResp(Device device, String roleName);
}

package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备分页查询")
public class DevicePageReq extends BasePageReq {

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "设备状态")
    private String state;

    @Schema(description = "角色ID")
    private Integer roleId;
}

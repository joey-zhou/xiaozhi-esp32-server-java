package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新设备请求")
public class DeviceUpdateReq {

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "角色ID")
    private Integer roleId;

    @Schema(description = "地理位置")
    private String location;
}

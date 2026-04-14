package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "设备同步请求")
public class DeviceSyncReq {

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "WiFi 名称")
    private String wifiName;

    @Schema(description = "IP")
    private String ip;

    @Schema(description = "地理位置")
    private String location;

    @Schema(description = "芯片型号")
    private String chipModelName;

    @Schema(description = "设备类型")
    private String type;

    @Schema(description = "固件版本")
    private String version;
}

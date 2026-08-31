package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "扫码绑定设备请求")
public class DeviceScanBindReq {

    @NotBlank(message = "设备ID不能为空")
    @Schema(description = "设备ID（MAC地址，来自设备二维码）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;
}

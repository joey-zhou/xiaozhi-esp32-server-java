package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "添加设备请求")
public class DeviceCreateReq {

    @NotBlank(message = "设备验证码不能为空")
    @Schema(description = "设备验证码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(description = "绑定的角色，不传用当前用户的默认角色")
    private Integer roleId;
}

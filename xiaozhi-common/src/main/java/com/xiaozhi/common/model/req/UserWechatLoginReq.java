package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信登录请求")
public class UserWechatLoginReq {

    @Schema(description = "微信登录 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "微信登录code不能为空")
    private String code;

    @Schema(description = "邀请人ID")
    private Integer inviterId;
}

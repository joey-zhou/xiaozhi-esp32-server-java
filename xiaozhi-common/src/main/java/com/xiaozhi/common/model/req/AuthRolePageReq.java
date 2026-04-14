package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "后台权限角色分页查询")
public class AuthRolePageReq extends BasePageReq {

    @Schema(description = "角色名称")
    private String authRoleName;

    @Schema(description = "角色标识")
    private String roleKey;

    @Schema(description = "状态")
    private String status;
}

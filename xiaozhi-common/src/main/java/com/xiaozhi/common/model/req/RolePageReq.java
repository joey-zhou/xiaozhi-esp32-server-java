package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "角色分页查询")
public class RolePageReq extends BasePageReq {

    @Schema(description = "角色ID")
    private Integer roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "是否默认角色(1是 0否)")
    private String isDefault;

    @Schema(description = "状态(1启用 0禁用)")
    private String state;
}

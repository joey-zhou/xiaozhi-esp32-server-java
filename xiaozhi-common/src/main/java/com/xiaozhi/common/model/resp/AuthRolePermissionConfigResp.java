package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "后台权限角色授权配置")
public class AuthRolePermissionConfigResp extends AuthRoleResp {

    @Schema(description = "权限树")
    private List<PermissionTreeResp> permissionTree;

    @Schema(description = "当前已选权限ID")
    private List<Integer> checkedPermissionIds;
}

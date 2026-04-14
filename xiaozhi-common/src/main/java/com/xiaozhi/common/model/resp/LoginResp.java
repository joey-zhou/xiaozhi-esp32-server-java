package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResp {

    @Schema(description = "访问令牌")
    private String token;

    @Schema(description = "刷新令牌")
    private String refreshToken;

    @Schema(description = "过期时间（秒）")
    private Integer expiresIn;

    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "是否新用户")
    private Boolean isNewUser;

    @Schema(description = "用户信息")
    private UserResp user;

    @Schema(description = "后台权限角色")
    private AuthRoleResp authRole;

    @Schema(description = "权限树")
    private List<PermissionTreeResp> permissions;
}

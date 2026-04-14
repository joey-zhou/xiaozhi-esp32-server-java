package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户分页查询")
public class UserPageReq extends BasePageReq {

    @Schema(description = "姓名/昵称")
    private String name;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String tel;

    @Schema(description = "是否管理员")
    private String isAdmin;

    @Schema(description = "后台权限角色ID")
    private Integer authRoleId;
}

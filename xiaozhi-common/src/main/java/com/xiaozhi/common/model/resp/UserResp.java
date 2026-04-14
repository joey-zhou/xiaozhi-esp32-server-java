package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "用户信息")
public class UserResp {

    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "姓名/昵称")
    private String name;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String tel;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "状态")
    private String state;

    @Schema(description = "是否管理员")
    private String isAdmin;

    @Schema(description = "后台权限角色ID")
    private Integer authRoleId;

    @Schema(description = "后台权限角色名称")
    private String authRoleName;

    @Schema(description = "累计消息数")
    private Integer totalMessage;

    @Schema(description = "设备总数")
    private Integer totalDevice;

    @Schema(description = "在线设备数")
    private Integer aliveNumber;

    @Schema(description = "最后登录IP")
    private String loginIp;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最后登录时间")
    private LocalDateTime loginTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

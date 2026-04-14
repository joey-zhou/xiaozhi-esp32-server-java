package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "后台权限角色")
public class AuthRoleResp {

    @Schema(description = "角色ID")
    private Integer authRoleId;

    @Schema(description = "角色名称")
    private String authRoleName;

    @Schema(description = "角色标识")
    private String roleKey;

    @Schema(description = "角色描述")
    private String description;

    @Schema(description = "状态")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

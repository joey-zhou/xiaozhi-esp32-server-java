package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "权限信息")
public class PermissionResp {

    @Schema(description = "权限ID")
    private Integer permissionId;

    @Schema(description = "父权限ID")
    private Integer parentId;

    @Schema(description = "权限名称")
    private String name;

    @Schema(description = "权限标识")
    private String permissionKey;

    @Schema(description = "权限类型")
    private String permissionType;

    @Schema(description = "路由路径")
    private String path;

    @Schema(description = "前端组件路径")
    private String component;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "是否可见")
    private String visible;

    @Schema(description = "状态")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

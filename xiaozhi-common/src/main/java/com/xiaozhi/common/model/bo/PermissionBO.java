package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PermissionBO {

    private Integer permissionId;
    private Integer parentId;
    private String name;
    private String permissionKey;
    private String permissionType;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private String visible;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 只有树形查询会填，平铺查询为 null。 */
    private List<PermissionBO> children;
}

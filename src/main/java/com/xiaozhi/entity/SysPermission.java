package com.xiaozhi.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 权限类
 *
 * @author Joey
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysPermission extends Base<SysPermission> {
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
    
    // 非数据库字段
    private List<SysPermission> children;

}
package com.xiaozhi.entity;


import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 角色权限配置
 *
 * @author Joey
 *
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SysRolePermission extends Base<SysRolePermission> {
    private Integer id;
    private Integer roleId;
    private Integer permissionId;
}
package com.xiaozhi.service;

import com.xiaozhi.entity.SysPermission;

import java.util.List;

public interface SysPermissionService {
    /**
     * 查询所有权限
     */
    List<SysPermission> selectAll();
    
    /**
     * 根据ID查询权限
     */
    SysPermission selectById(Integer permissionId);
    
    /**
     * 根据权限类型查询权限
     */
    List<SysPermission> selectByType(String permissionType);
    
    /**
     * 根据父ID查询子权限
     */
    List<SysPermission> selectByParentId(Integer parentId);
    
    /**
     * 根据角色ID查询权限
     */
    List<SysPermission> selectByRoleId(Integer roleId);
    
    /**
     * 根据用户ID查询权限
     */
    List<SysPermission> selectByUserId(Integer userId);
    
    /**
     * 构建权限树
     */
    List<SysPermission> buildPermissionTree(List<SysPermission> permissions);
    
    /**
     * 添加权限
     */
    int add(SysPermission permission);
    
    /**
     * 更新权限
     */
    int update(SysPermission permission);
    
    /**
     * 删除权限
     */
    int delete(Integer permissionId);
}
package com.xiaozhi.dao;

import com.xiaozhi.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RolePermissionMapper {
    /**
     * 根据角色ID查询角色权限关联
     */
    List<SysRolePermission> selectByRoleId(Integer roleId);
    
    /**
     * 添加角色权限关联
     */
    int add(SysRolePermission rolePermission);
    
    /**
     * 批量添加角色权限关联
     */
    int batchAdd(@Param("list") List<SysRolePermission> list);
    
    /**
     * 根据角色ID删除角色权限关联
     */
    int deleteByRoleId(Integer roleId);
    
    /**
     * 根据角色ID和权限ID删除角色权限关联
     */
    int deleteByRoleIdAndPermissionId(@Param("roleId") Integer roleId, @Param("permissionId") Integer permissionId);
}
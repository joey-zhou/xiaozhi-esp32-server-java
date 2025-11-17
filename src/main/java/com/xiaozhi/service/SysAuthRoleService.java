package com.xiaozhi.service;

import com.xiaozhi.entity.SysAuthRole;
import com.xiaozhi.entity.SysPermission;

import java.util.List;


/**
 * 用户角色接口
 * 
 * @author Joey
 */
public interface SysAuthRoleService {
    /**
     * 查询所有角色
     */
    List<SysAuthRole> selectAll();
    
    /**
     * 根据ID查询角色
     */
    SysAuthRole selectById(Integer roleId);
    
    /**
     * 根据用户ID查询角色
     */
    List<SysAuthRole> selectByUserId(Integer userId);
    
    /**
     * 添加角色
     */
    int add(SysAuthRole role);
    
    /**
     * 更新角色
     */
    int update(SysAuthRole role);
    
    /**
     * 删除角色
     */
    int delete(Integer roleId);
    
    /**
     * 分配权限
     */
    int assignPermissions(Integer roleId, List<Integer> permissionIds);
    
    /**
     * 获取角色权限
     */
    List<SysPermission> getRolePermissions(Integer roleId);
}
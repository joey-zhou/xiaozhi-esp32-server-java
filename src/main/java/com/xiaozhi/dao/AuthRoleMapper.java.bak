package com.xiaozhi.dao;

import com.xiaozhi.entity.SysAuthRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AuthRoleMapper {
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
}
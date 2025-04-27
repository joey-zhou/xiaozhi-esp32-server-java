package com.xiaozhi.service;

import java.util.List;

import com.xiaozhi.entity.SysRole;

/**
 * 角色查询/更新
 * 
 * @author Joey
 * 
 */
public interface SysRoleService {

  /**
   * 添加角色
   * 
   * @param role
   * @return
   */
  public int add(SysRole role);

  /**
   * 查询角色信息
   * 
   * @param role
   * @return
   */
  public List<SysRole> query(SysRole role);

  /**
   * 更新角色信息
   * 
   * @param role
   * @return
   */
  public int update(SysRole role);

  public SysRole selectRoleById(Integer roleId);

}
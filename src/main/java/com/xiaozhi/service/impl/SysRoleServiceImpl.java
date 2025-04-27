package com.xiaozhi.service.impl;

import java.util.List;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.dao.RoleMapper;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysRoleService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 角色操作
 *
 * @author Joey
 *
 */

@Service
public class SysRoleServiceImpl implements SysRoleService {

    @Resource
    private RoleMapper roleMapper;

    /**
     * 添加角色
     *
     * @param role
     * @return
     */
    @Override
    @Transactional
    public int add(SysRole role) {
        // 如果当前配置被设置为默认，则将同类型同用户的其他配置设置为非默认
        if (role.getIsDefault() != null && role.getIsDefault().equals("1")) {
            roleMapper.resetDefault(role);
        }
        // 添加角色
        return roleMapper.add(role);

    }

    /**
     * 查询角色信息
     *
     * @param role
     * @return
     */
    @Override
    public List<SysRole> query(SysRole role) {
        if (role.getLimit() != null && role.getLimit() > 0) {
            PageHelper.startPage(role.getStart(), role.getLimit());
        }
        return roleMapper.query(role);
    }

    /**
     * 更新角色信息
     *
     * @param role
     * @return
     */
    @Override
    @Transactional
    public int update(SysRole role) {
        // 如果当前配置被设置为默认，则将同类型同用户的其他配置设置为非默认
        if (role.getIsDefault() != null && role.getIsDefault().equals("1")) {
            roleMapper.resetDefault(role);
        }
        return roleMapper.update(role);
    }

    @Override
    public SysRole selectRoleById(Integer roleId) {
        return roleMapper.selectRoleById(roleId);
    }
}
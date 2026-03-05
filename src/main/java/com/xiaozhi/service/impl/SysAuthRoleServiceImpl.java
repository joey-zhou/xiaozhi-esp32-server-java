package com.xiaozhi.service.impl;

import com.xiaozhi.entity.SysAuthRole;
import com.xiaozhi.entity.SysPermission;
import com.xiaozhi.entity.SysRolePermission;
import com.xiaozhi.repository.SysAuthRoleRepository;
import com.xiaozhi.repository.SysPermissionRepository;
import com.xiaozhi.repository.SysRolePermissionRepository;
import com.xiaozhi.service.SysAuthRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SysAuthRoleServiceImpl implements SysAuthRoleService {

    @Autowired
    private SysAuthRoleRepository sysAuthRoleRepository;

    @Autowired
    private SysPermissionRepository sysPermissionRepository;

    @Autowired
    private SysRolePermissionRepository sysRolePermissionRepository;

    @Override
    public List<SysAuthRole> selectAll() {
        return sysAuthRoleRepository.findAll();
    }

    @Override
    public SysAuthRole selectById(Integer roleId) {
        return sysAuthRoleRepository.findRoleById(roleId);
    }

    @Override
    public List<SysAuthRole> selectByUserId(Integer userId) {
        return sysAuthRoleRepository.selectByUserId(userId);
    }

    @Override
    public int add(SysAuthRole role) {
        sysAuthRoleRepository.save(role);
        return 1;
    }

    @Override
    public int update(SysAuthRole role) {
        sysAuthRoleRepository.save(role);
        return 1;
    }

    @Override
    public int delete(Integer roleId) {
        // 先删除角色权限关联
        sysRolePermissionRepository.deleteByRoleId(roleId);
        // 再删除角色
        sysAuthRoleRepository.deleteById(roleId);
        return 1;
    }

    @Override
    @Transactional
    public int assignPermissions(Integer roleId, List<Integer> permissionIds) {
        // 先删除原有的角色权限关联
        sysRolePermissionRepository.deleteByRoleId(roleId);

        // 如果权限 ID 列表为空，则直接返回
        if (permissionIds == null || permissionIds.isEmpty()) {
            return 0;
        }

        // 批量添加新的角色权限关联
        List<SysRolePermission> rolePermissions = new ArrayList<>();
        for (Integer permissionId : permissionIds) {
            SysRolePermission rolePermission = new SysRolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            rolePermissions.add(rolePermission);
        }

        sysRolePermissionRepository.saveAll(rolePermissions);
        return rolePermissions.size();
    }

    @Override
    public List<SysPermission> getRolePermissions(Integer roleId) {
        return sysPermissionRepository.findPermissionsByRoleId(roleId);
    }
}

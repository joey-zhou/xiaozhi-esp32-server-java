package com.xiaozhi.service.impl;

import com.xiaozhi.dao.PermissionMapper;
import com.xiaozhi.entity.SysPermission;
import com.xiaozhi.service.SysPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SysPermissionServiceImpl implements SysPermissionService {

    @Autowired
    private PermissionMapper permissionMapper;

    @Override
    public List<SysPermission> selectAll() {
        return permissionMapper.selectAll();
    }

    @Override
    public SysPermission selectById(Integer permissionId) {
        return permissionMapper.selectById(permissionId);
    }

    @Override
    public List<SysPermission> selectByType(String permissionType) {
        return permissionMapper.selectByType(permissionType);
    }

    @Override
    public List<SysPermission> selectByParentId(Integer parentId) {
        return permissionMapper.selectByParentId(parentId);
    }

    @Override
    public List<SysPermission> selectByRoleId(Integer roleId) {
        return permissionMapper.selectByRoleId(roleId);
    }

    @Override
    public List<SysPermission> selectByUserId(Integer userId) {
        return permissionMapper.selectByUserId(userId);
    }

    @Override
    public List<SysPermission> buildPermissionTree(List<SysPermission> permissions) {
        List<SysPermission> returnList = new ArrayList<>();
        
        // 先找出所有的一级菜单
        for (SysPermission permission : permissions) {
            // 一级菜单没有parentId或parentId为0
            if (permission.getParentId() == null || permission.getParentId() == 0) {
                permission.setChildren(new ArrayList<>());
                returnList.add(permission);
            }
        }
        
        // 为一级菜单设置子菜单
        for (SysPermission permission : permissions) {
            if (permission.getParentId() != null && permission.getParentId() != 0) {
                // 获取父菜单
                for (SysPermission parent : returnList) {
                    if (parent.getPermissionId().equals(permission.getParentId())) {
                        if (parent.getChildren() == null) {
                            parent.setChildren(new ArrayList<>());
                        }
                        parent.getChildren().add(permission);
                        break;
                    }
                }
            }
        }
        
        return returnList;
    }

    @Override
    public int add(SysPermission permission) {
        return permissionMapper.add(permission);
    }

    @Override
    public int update(SysPermission permission) {
        return permissionMapper.update(permission);
    }

    @Override
    public int delete(Integer permissionId) {
        return permissionMapper.delete(permissionId);
    }
}
package com.xiaozhi.authrole.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.authrole.convert.AuthRoleConvert;
import com.xiaozhi.authrole.dal.mysql.dataobject.AuthRoleDO;
import com.xiaozhi.authrole.dal.mysql.mapper.AuthRoleMapper;
import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.authrolepermission.dal.mysql.dataobject.AuthRolePermissionDO;
import com.xiaozhi.authrolepermission.dal.mysql.mapper.AuthRolePermissionMapper;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.AuthRoleBO;
import com.xiaozhi.permission.service.PermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class AuthRoleServiceImpl implements AuthRoleService {

    @Resource
    private AuthRoleMapper authRoleMapper;

    @Resource
    private AuthRolePermissionMapper authRolePermissionMapper;

    @Resource
    private PermissionService permissionService;

    @Resource
    private AuthRoleConvert authRoleConvert;

    @Override
    public PageResult<AuthRoleBO> page(int pageNo, int pageSize, String authRoleName, String roleKey, String status) {
        Page<AuthRoleDO> page = new Page<>(pageNo, pageSize);
        IPage<AuthRoleDO> result = authRoleMapper.selectPage(page, new LambdaQueryWrapper<AuthRoleDO>()
            .like(StringUtils.hasText(authRoleName), AuthRoleDO::getAuthRoleName, authRoleName)
            .eq(StringUtils.hasText(roleKey), AuthRoleDO::getRoleKey, roleKey)
            .eq(StringUtils.hasText(status), AuthRoleDO::getStatus, status)
            .orderByAsc(AuthRoleDO::getAuthRoleId));

        return new PageResult<>(
            result.getRecords().stream().map(authRoleConvert::toBO).toList(),
            result.getTotal(),
            Math.toIntExact(result.getCurrent()),
            Math.toIntExact(result.getSize())
        );
    }

    @Override
    public AuthRoleBO getBO(Integer authRoleId) {
        if (authRoleId == null) {
            return null;
        }
        return authRoleConvert.toBO(authRoleMapper.selectById(authRoleId));
    }

    @Override
    public String getRoleKey(Integer authRoleId) {
        if (authRoleId == null) {
            return null;
        }
        AuthRoleDO authRole = authRoleMapper.selectById(authRoleId);
        return authRole == null ? null : authRole.getRoleKey();
    }

    @Override
    @Transactional
    public void assignPermissions(Integer authRoleId, List<Integer> permissionIds) {
        if (authRoleId == null || authRoleMapper.selectById(authRoleId) == null) {
            throw new ResourceNotFoundException("权限角色不存在");
        }
        authRolePermissionMapper.delete(new LambdaUpdateWrapper<AuthRolePermissionDO>()
            .eq(AuthRolePermissionDO::getAuthRoleId, authRoleId));
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<AuthRolePermissionDO> list = permissionIds.stream()
                .filter(Objects::nonNull)
                .map(permissionId -> {
                    AuthRolePermissionDO relation = new AuthRolePermissionDO();
                    relation.setAuthRoleId(authRoleId);
                    relation.setPermissionId(permissionId);
                    return relation;
                })
                .toList();
            if (!list.isEmpty()) {
                authRolePermissionMapper.insertBatch(list);
            }
        }
        permissionService.clearAuthRoleCache(authRoleId);
    }
}

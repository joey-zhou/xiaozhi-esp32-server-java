package com.xiaozhi.authrole.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.authrole.convert.AuthRoleConvert;
import com.xiaozhi.authrole.dal.mysql.dataobject.AuthRoleDO;
import com.xiaozhi.authrole.dal.mysql.mapper.AuthRoleMapper;
import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.resp.AuthRolePermissionConfigResp;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.PermissionResp;
import com.xiaozhi.permission.service.PermissionService;
import com.xiaozhi.authrolepermission.dal.mysql.dataobject.AuthRolePermissionDO;
import com.xiaozhi.authrolepermission.dal.mysql.mapper.AuthRolePermissionMapper;
import com.xiaozhi.user.dal.mysql.dataobject.UserDO;
import com.xiaozhi.user.dal.mysql.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthRoleServiceImpl implements AuthRoleService {

    @Resource
    private AuthRoleMapper authRoleMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private AuthRolePermissionMapper authRolePermissionMapper;

    @Resource
    private PermissionService permissionService;

    @Resource
    private AuthRoleConvert authRoleConvert;

    @Override
    public PageResp<AuthRoleResp> page(int pageNo, int pageSize, String authRoleName, String roleKey, String status) {
        Page<AuthRoleDO> page = new Page<>(pageNo, pageSize);
        IPage<AuthRoleDO> result = authRoleMapper.selectPage(page, new LambdaQueryWrapper<AuthRoleDO>()
            .like(StringUtils.hasText(authRoleName), AuthRoleDO::getAuthRoleName, authRoleName)
            .eq(StringUtils.hasText(roleKey), AuthRoleDO::getRoleKey, roleKey)
            .eq(StringUtils.hasText(status), AuthRoleDO::getStatus, status)
            .orderByAsc(AuthRoleDO::getAuthRoleId));

        return new PageResp<>(
            result.getRecords().stream().map(authRoleConvert::toResp).toList(),
            result.getTotal(),
            Math.toIntExact(result.getCurrent()),
            Math.toIntExact(result.getSize())
        );
    }

    @Override
    public AuthRoleResp get(Integer authRoleId) {
        if (authRoleId == null) {
            throw new IllegalArgumentException("权限角色ID不能为空");
        }
        AuthRoleResp result = authRoleConvert.toResp(authRoleMapper.selectById(authRoleId));
        if (result == null) {
            throw new ResourceNotFoundException("权限角色不存在");
        }
        return result;
    }

    @Override
    public AuthRolePermissionConfigResp getPermissionConfig(Integer authRoleId) {
        AuthRoleResp authRole = get(authRoleId);

        AuthRolePermissionConfigResp resp = new AuthRolePermissionConfigResp();
        resp.setAuthRoleId(authRole.getAuthRoleId());
        resp.setAuthRoleName(authRole.getAuthRoleName());
        resp.setRoleKey(authRole.getRoleKey());
        resp.setDescription(authRole.getDescription());
        resp.setStatus(authRole.getStatus());
        resp.setCreateTime(authRole.getCreateTime());
        resp.setUpdateTime(authRole.getUpdateTime());
        resp.setPermissionTree(permissionService.listTree());
        resp.setCheckedPermissionIds(permissionService.listIdsByAuthRoleId(authRoleId));
        return resp;
    }

    @Override
    public AuthRoleResp getByUserId(Integer userId) {
        if (userId == null) {
            return null;
        }
        UserDO user = userMapper.selectById(userId);
        return user == null ? null : get(user.getAuthRoleId());
    }

    @Override
    @Transactional
    public void assignPermissions(Integer authRoleId, List<Integer> permissionIds) {
        get(authRoleId);
        authRolePermissionMapper.delete(new LambdaUpdateWrapper<AuthRolePermissionDO>()
            .eq(AuthRolePermissionDO::getAuthRoleId, authRoleId));
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<AuthRolePermissionDO> list = permissionIds.stream()
                .filter(id -> id != null)
                .map(permissionId -> {
                    AuthRolePermissionDO relation = new AuthRolePermissionDO();
                    relation.setAuthRoleId(authRoleId);
                    relation.setPermissionId(permissionId);
                    return relation;
                })
                .collect(Collectors.toList());
            if (!list.isEmpty()) {
                authRolePermissionMapper.insertBatch(list);
            }
        }
        permissionService.clearAuthRoleCache(authRoleId);
    }

    @Override
    public List<PermissionResp> listPermissions(Integer authRoleId) {
        return permissionService.listByAuthRoleId(authRoleId);
    }
}

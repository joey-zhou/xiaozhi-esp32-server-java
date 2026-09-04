package com.xiaozhi.authrole;

import com.xiaozhi.authrole.convert.AuthRoleConvert;
import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.AuthRoleBO;
import com.xiaozhi.common.model.req.AuthRolePageReq;
import com.xiaozhi.common.model.resp.AuthRolePermissionConfigResp;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import com.xiaozhi.permission.convert.PermissionConvert;
import com.xiaozhi.permission.service.PermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 后台权限角色应用服务：分页、授权配置的组装与保存。
 */
@Service
public class AuthRoleAppService {

    @Resource
    private AuthRoleService authRoleService;

    @Resource
    private PermissionService permissionService;

    @Resource
    private AuthRoleConvert authRoleConvert;

    @Resource
    private PermissionConvert permissionConvert;

    public PageResult<AuthRoleResp> page(AuthRolePageReq req) {
        AuthRolePageReq r = req == null ? new AuthRolePageReq() : req;
        return authRoleService.page(r.getPageNo(), r.getPageSize(), r.getAuthRoleName(), r.getRoleKey(), r.getStatus())
            .map(authRoleConvert::toResp);
    }

    public AuthRolePermissionConfigResp getPermissionConfig(Integer authRoleId) {
        AuthRoleBO authRole = authRoleService.getBO(authRoleId);
        if (authRole == null) {
            throw new ResourceNotFoundException("权限角色不存在");
        }
        AuthRolePermissionConfigResp resp = authRoleConvert.toPermissionConfigResp(authRole);
        resp.setPermissionTree(permissionService.listTree().stream()
            .map(permissionConvert::toTreeResp)
            .toList());
        resp.setCheckedPermissionIds(permissionService.listIdsByAuthRoleId(authRoleId));
        return resp;
    }

    public AuthRolePermissionConfigResp assignPermissions(Integer authRoleId, List<Integer> permissionIds) {
        authRoleService.assignPermissions(authRoleId, permissionIds);
        return getPermissionConfig(authRoleId);
    }
}

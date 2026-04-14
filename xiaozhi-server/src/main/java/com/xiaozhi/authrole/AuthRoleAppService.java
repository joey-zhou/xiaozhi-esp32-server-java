package com.xiaozhi.authrole;

import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.common.model.req.AuthRolePageReq;
import com.xiaozhi.common.model.resp.AuthRolePermissionConfigResp;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import com.xiaozhi.common.model.resp.PageResp;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AuthRole 领域应用服务。
 * <p>
 * 职责：编排 Controller → Domain Service 之间的流程，包括：
 * <ul>
 *   <li>Req/Resp ↔ BO 转换</li>
 *   <li>权限角色管理编排</li>
 * </ul>
 */
@Service
public class AuthRoleAppService {

    @Resource
    private AuthRoleService authRoleService;

    public PageResp<AuthRoleResp> page(AuthRolePageReq req) {
        AuthRolePageReq r = req == null ? new AuthRolePageReq() : req;
        return authRoleService.page(r.getPageNo(), r.getPageSize(), r.getAuthRoleName(), r.getRoleKey(), r.getStatus());
    }

    public AuthRolePermissionConfigResp getPermissionConfig(Integer authRoleId) {
        return authRoleService.getPermissionConfig(authRoleId);
    }

    public AuthRolePermissionConfigResp assignPermissions(Integer authRoleId, List<Integer> permissionIds) {
        authRoleService.assignPermissions(authRoleId, permissionIds);
        return authRoleService.getPermissionConfig(authRoleId);
    }
}

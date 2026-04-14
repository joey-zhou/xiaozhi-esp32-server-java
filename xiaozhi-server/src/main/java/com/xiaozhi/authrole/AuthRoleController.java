package com.xiaozhi.authrole;

import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.authrole.AuthRoleAppService;
import com.xiaozhi.common.model.req.AuthRolePageReq;
import java.util.List;
import com.xiaozhi.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth-role")
@Tag(name = "后台权限角色", description = "后台权限角色相关操作")
public class AuthRoleController extends BaseController {

    @Resource
    private AuthRoleAppService authRoleAppService;

    @GetMapping("")
    @ResponseBody
    @SaCheckPermission("system:auth-role:api:list")
    @Operation(summary = "根据条件查询后台权限角色", description = "返回后台权限角色列表")
    public ApiResponse<?> list(@Valid AuthRolePageReq req) {
        return ApiResponse.success(authRoleAppService.page(req));
    }

    @GetMapping("/{authRoleId}/permissions")
    @ResponseBody
    @SaCheckPermission("system:auth-role:api:detail")
    @Operation(summary = "获取后台权限角色授权配置", description = "返回角色权限树和已选权限")
    public ApiResponse<?> getPermissionConfig(@PathVariable Integer authRoleId) {
        return ApiResponse.success(authRoleAppService.getPermissionConfig(authRoleId));
    }

    @PutMapping("/{authRoleId}/permissions")
    @ResponseBody
    @SaCheckPermission("system:auth-role:api:assign")
    @AuditLog(module = "权限管理", operation = "更新角色权限")
    @Operation(summary = "更新后台权限角色授权配置", description = "保存角色已选权限")
    public ApiResponse<?> assignPermissions(
        @PathVariable Integer authRoleId,
        @RequestBody(required = false) List<Integer> permissionIds
    ) {
        return ApiResponse.success(authRoleAppService.assignPermissions(authRoleId, permissionIds));
    }
}

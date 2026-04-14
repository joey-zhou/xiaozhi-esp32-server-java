package com.xiaozhi.mcpserver;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.model.req.McpGlobalToolStatusReq;
import com.xiaozhi.common.model.req.McpRoleExcludeToolsReq;
import com.xiaozhi.common.model.req.McpRoleToolStatusReq;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.ai.mcp.server.McpToolQueryService;
import com.xiaozhi.mcptoolexclude.service.McpToolExcludeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcpTool")
@Tag(name = "MCP工具管理", description = "MCP工具启用/禁用相关操作")
public class McpToolController {

    @Resource
    private McpToolExcludeService mcpToolExcludeService;

    @Resource
    private McpToolQueryService mcpToolQueryService;

    @PatchMapping("/role/{roleId}/tools")
    @SaCheckPermission("system:role:mcp-tools:api:update")
    @CheckOwner(resource = "role", id = "#roleId")
    @AuditLog(module = "MCP工具管理", operation = "切换角色工具状态")
    @Operation(summary = "切换角色工具状态", description = "启用或禁用指定角色的某个工具")
    public ApiResponse<?> toggleRoleToolStatus(@PathVariable Integer roleId, @Valid @RequestBody McpRoleToolStatusReq req) {
        mcpToolExcludeService.toggleRoleToolStatus(roleId, req.getToolName(), req.getServerName(), req.getEnabled());
        return ApiResponse.success("操作成功");
    }

    @PostMapping("/role/{roleId}/exclude-tools")
    @SaCheckPermission("system:role:mcp-tools:api:update")
    @CheckOwner(resource = "role", id = "#roleId")
    @AuditLog(module = "MCP工具管理", operation = "批量设置角色排除工具")
    @Operation(summary = "批量设置角色排除工具", description = "批量设置指定角色需要排除的工具列表")
    public ApiResponse<?> batchSetRoleExcludeTools(@PathVariable Integer roleId, @Valid @RequestBody McpRoleExcludeToolsReq req) {
        mcpToolExcludeService.batchSetRoleExcludeTools(roleId, req.getExcludeTools(), req.getServerName());
        return ApiResponse.success("批量设置成功");
    }

    @PatchMapping("/global/tools")
    @SaCheckPermission("system:config:mcpServer:api:update")
    @AuditLog(module = "MCP工具管理", operation = "切换全局工具状态")
    @Operation(summary = "切换全局工具状态", description = "启用或禁用全局工具")
    public ApiResponse<?> toggleGlobalToolStatus(@Valid @RequestBody McpGlobalToolStatusReq req) {
        mcpToolExcludeService.toggleGlobalToolStatus(req.getToolName(), req.getServerName(), req.getEnabled());
        return ApiResponse.success("操作成功");
    }

    @GetMapping("/role/{roleId}/disabled-tools")
    @SaCheckPermission("system:role:mcp-tools:api:list")
    @CheckOwner(resource = "role", id = "#roleId != null && #roleId > 0 ? #roleId : null")
    @Operation(summary = "获取禁用的工具列表", description = "获取指定角色和全局禁用的工具列表")
    public ApiResponse<?> getDisabledTools(@PathVariable Integer roleId) {
        List<String> roleDisabled = roleId != null && roleId > 0 ? mcpToolExcludeService.getRoleDisabledTools(roleId) : List.of();
        List<String> globalDisabled = mcpToolExcludeService.getGlobalDisabledTools();

        Map<String, List<String>> result = new HashMap<>();
        result.put("roleDisabled", roleDisabled);
        result.put("globalDisabled", globalDisabled);

        return ApiResponse.success(result);
    }

    @GetMapping("/system-global")
    @SaCheckPermission("system:role:mcp-tools:api:system-global")
    @Operation(summary = "获取系统全局工具列表", description = "获取系统中所有可用的全局工具列表")
    public ApiResponse<?> getSystemGlobalTools() {
        return ApiResponse.success(mcpToolQueryService.getSystemGlobalToolSummaries());
    }
}

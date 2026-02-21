package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.dto.param.BatchSetRoleExcludeToolsParam;
import com.xiaozhi.dto.param.ToggleGlobalToolStatusParam;
import com.xiaozhi.dto.param.ToggleRoleToolStatusParam;
import com.xiaozhi.service.McpToolExcludeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP工具管理控制器
 */
@RestController
@RequestMapping("/api/mcpTool")
@Tag(name = "MCP工具管理", description = "MCP工具启用/禁用相关操作")
public class McpToolController {
    
    private static final Logger logger = LoggerFactory.getLogger(McpToolController.class);
    
    @Autowired
    private McpToolExcludeService mcpToolExcludeService;
    
    /**
     * 切换角色工具状态(单个工具)
     */
    @PostMapping("/toggleStatus")
    @Operation(summary = "切换角色工具状态", description = "启用或禁用指定角色的某个工具")
    public ResultMessage toggleRoleToolStatus(@Valid @RequestBody ToggleRoleToolStatusParam param) {
        try {
            mcpToolExcludeService.toggleRoleToolStatus(
                param.getRoleId(),
                param.getToolName(),
                param.getServerName(),
                param.getEnabled()
            );

            return ResultMessage.success("操作成功");
        } catch (Exception e) {
            logger.error("切换角色工具状态失败", e);
            return ResultMessage.error("操作失败: " + e.getMessage());
        }
    }

    /**
     * 批量设置角色排除工具
     */
    @PostMapping("/batchSetExcludeTools")
    @Operation(summary = "批量设置角色排除工具", description = "批量设置指定角色需要排除的工具列表")
    public ResultMessage batchSetRoleExcludeTools(@Valid @RequestBody BatchSetRoleExcludeToolsParam param) {
        try {
            mcpToolExcludeService.batchSetRoleExcludeTools(
                param.getRoleId(),
                param.getExcludeTools(),
                param.getServerName()
            );

            return ResultMessage.success("批量设置成功");
        } catch (Exception e) {
            logger.error("批量设置角色排除工具失败", e);
            return ResultMessage.error("操作失败: " + e.getMessage());
        }
    }
    
    /**
     * 切换全局工具状态
     */
    @PostMapping("/toggleGlobalStatus")
    @Operation(summary = "切换全局工具状态", description = "启用或禁用全局工具")
    public ResultMessage toggleGlobalToolStatus(@Valid @RequestBody ToggleGlobalToolStatusParam param) {
        try {
            mcpToolExcludeService.toggleGlobalToolStatus(
                param.getToolName(),
                param.getServerName(),
                param.getEnabled()
            );

            return ResultMessage.success("操作成功");
        } catch (Exception e) {
            logger.error("切换全局工具状态失败", e);
            return ResultMessage.error("操作失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取角色禁用的工具列表
     */
    @GetMapping("/getDisabledTools")
    @Operation(summary = "获取禁用的工具列表", description = "获取指定角色和全局禁用的工具列表")
    public ResultMessage getDisabledTools(@RequestParam Integer roleId) {
        try {
            List<String> roleDisabled = mcpToolExcludeService.getRoleDisabledTools(roleId);
            List<String> globalDisabled = mcpToolExcludeService.getGlobalDisabledTools();

            Map<String, List<String>> result = new HashMap<>();
            result.put("roleDisabled", roleDisabled);
            result.put("globalDisabled", globalDisabled);

            return ResultMessage.success(result);
        } catch (Exception e) {
            logger.error("获取禁用工具列表失败", e);
            return ResultMessage.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取系统全局工具列表
     */
    @GetMapping("/getSystemGlobalTools")
    @Operation(summary = "获取系统全局工具列表", description = "获取系统中所有可用的全局工具列表")
    public ResultMessage getSystemGlobalTools() {
        try {
            List<String> systemTools = mcpToolExcludeService.getSystemGlobalTools();
            return ResultMessage.success(systemTools);
        } catch (Exception e) {
            logger.error("获取系统全局工具列表失败", e);
            return ResultMessage.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 刷新缓存
     */
    @PostMapping("/refreshCache")
    @Operation(summary = "刷新缓存", description = "刷新MCP工具相关缓存")
    public ResultMessage refreshCache() {
        try {
            mcpToolExcludeService.refreshCache();
            return ResultMessage.success("刷新成功");
        } catch (Exception e) {
            logger.error("刷新缓存失败", e);
            return ResultMessage.error("刷新失败: " + e.getMessage());
        }
    }
}

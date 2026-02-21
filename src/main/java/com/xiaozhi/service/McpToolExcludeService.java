package com.xiaozhi.service;

import java.util.List;
import java.util.Set;

/**
 * MCP工具过滤服务接口
 */
public interface McpToolExcludeService {
    
    /**
     * 获取用户相关的排除工具列表
     * @param userId 用户ID
     * @param roleId 角色ID
     * @param bindType 绑定类型
     * @param bindCode 绑定代码
     * @return 排除的工具名称集合
     */
    Set<String> getExcludedTools(Integer userId, Integer roleId);
    
    /**
     * 切换角色工具状态
     * @param roleId 角色ID
     * @param toolName 工具名称
     * @param serverName 服务器名称
     * @param enabled 是否启用
     */
    void toggleRoleToolStatus(Integer roleId, String toolName, String serverName, boolean enabled);
    
    /**
     * 切换全局工具状态
     * @param toolName 工具名称
     * @param serverName 服务器名称
     * @param enabled 是否启用
     */
    void toggleGlobalToolStatus(String toolName, String serverName, boolean enabled);
    
    /**
     * 获取角色禁用的工具列表
     * @param roleId 角色ID
     * @return 禁用的工具列表
     */
    List<String> getRoleDisabledTools(Integer roleId);
    
    /**
     * 获取全局禁用的工具列表
     * @return 全局禁用的工具列表
     */
    List<String> getGlobalDisabledTools();
    
    /**
     * 获取系统全局工具列表
     * @return 系统全局工具列表
     */
    List<String> getSystemGlobalTools();
    
    /**
     * 刷新缓存
     */
    void refreshCache();
    
    /**
     * 批量设置角色排除工具
     * @param roleId 角色ID
     * @param excludeTools 要排除的工具列表
     * @param serverName 服务器名称（可选，null则适用所有服务器）
     */
    void batchSetRoleExcludeTools(Integer roleId, List<String> excludeTools, String serverName);
}

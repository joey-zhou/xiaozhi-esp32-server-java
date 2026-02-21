package com.xiaozhi.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.dao.McpToolExcludeMapper;
import com.xiaozhi.entity.SysMcpToolExclude;
import com.xiaozhi.service.McpToolExcludeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * MCP工具过滤服务实现
 */
@Service
public class McpToolExcludeServiceImpl extends BaseServiceImpl implements McpToolExcludeService {
    
    private static final Logger logger = LoggerFactory.getLogger(McpToolExcludeServiceImpl.class);
    private static final String CACHE_NAME = "XiaoZhi:McpToolExclude";
    
    @Autowired
    private McpToolExcludeMapper mcpToolExcludeMapper;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    @Cacheable(value = CACHE_NAME, key = "'excluded_tools:' + #userId + ':' + #roleId")
    public Set<String> getExcludedTools(Integer userId, Integer roleId) {
        Set<String> excludedTools = new HashSet<>();
        
        // 查询全局排除工具
        Set<String> globalExcluded = getGlobalExcludedTools();
        excludedTools.addAll(globalExcluded);
        
        // 查询角色排除工具
        if (roleId != null) {
            Set<String> roleExcluded = getRoleExcludedTools(roleId);
            excludedTools.addAll(roleExcluded);
        }
        
        return excludedTools;
    }
    
    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void toggleRoleToolStatus(Integer roleId, String toolName, String serverName, boolean enabled) {
        String bindType = "mcp_server";
        String bindCode = serverName;
        String bindKey = roleId.toString();
        
        // 查询现有配置
        List<SysMcpToolExclude> existingConfigs = mcpToolExcludeMapper.selectByCondition(
            "role", bindType, bindCode, bindKey);
        
        if (enabled) {
            // 启用工具：从排除列表中移除
            if (!existingConfigs.isEmpty()) {
                SysMcpToolExclude config = existingConfigs.get(0);
                List<String> excludeList = parseExcludeTools(config.getExcludeTools());
                excludeList.remove(toolName);
                
                if (excludeList.isEmpty()) {
                    // 如果没有排除的工具了，删除配置
                    mcpToolExcludeMapper.delete(config.getId());
                } else {
                    // 更新排除列表
                    try {
                        config.setExcludeTools(objectMapper.writeValueAsString(excludeList));
                        config.setUpdateTime(LocalDateTime.now());
                        mcpToolExcludeMapper.update(config);
                    } catch (Exception e) {
                        logger.error("更新排除工具列表失败", e);
                        throw new RuntimeException("更新失败", e);
                    }
                }
            }
        } else {
            // 禁用工具：添加到排除列表
            if (existingConfigs.isEmpty()) {
                // 创建新配置
                SysMcpToolExclude newConfig = new SysMcpToolExclude();
                newConfig.setExcludeType("role");
                newConfig.setBindType(bindType);
                newConfig.setBindCode(bindCode);
                newConfig.setBindKey(bindKey);
                try {
                    newConfig.setExcludeTools(objectMapper.writeValueAsString(Arrays.asList(toolName)));
                    newConfig.setCreateTime(LocalDateTime.now());
                    newConfig.setUpdateTime(LocalDateTime.now());
                    mcpToolExcludeMapper.add(newConfig);
                } catch (Exception e) {
                    logger.error("创建排除工具配置失败", e);
                    throw new RuntimeException("创建失败", e);
                }
            } else {
                // 更新现有配置
                SysMcpToolExclude config = existingConfigs.get(0);
                List<String> excludeList = parseExcludeTools(config.getExcludeTools());
                if (!excludeList.contains(toolName)) {
                    excludeList.add(toolName);
                    try {
                        config.setExcludeTools(objectMapper.writeValueAsString(excludeList));
                        config.setUpdateTime(LocalDateTime.now());
                        mcpToolExcludeMapper.update(config);
                    } catch (Exception e) {
                        logger.error("更新排除工具列表失败", e);
                        throw new RuntimeException("更新失败", e);
                    }
                }
            }
        }
    }
    
    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void toggleGlobalToolStatus(String toolName, String serverName, boolean enabled) {
        String bindType = "mcp_server";
        String bindCode = serverName;
        String bindKey = "0";
        
        // 查询现有配置
        List<SysMcpToolExclude> existingConfigs = mcpToolExcludeMapper.selectByCondition(
            "global", bindType, bindCode, bindKey);
        
        if (enabled) {
            // 启用工具：从排除列表中移除
            if (!existingConfigs.isEmpty()) {
                SysMcpToolExclude config = existingConfigs.get(0);
                List<String> excludeList = parseExcludeTools(config.getExcludeTools());
                excludeList.remove(toolName);
                
                if (excludeList.isEmpty()) {
                    // 如果没有排除的工具了，删除配置
                    mcpToolExcludeMapper.delete(config.getId());
                } else {
                    // 更新排除列表
                    try {
                        config.setExcludeTools(objectMapper.writeValueAsString(excludeList));
                        config.setUpdateTime(LocalDateTime.now());
                        mcpToolExcludeMapper.update(config);
                    } catch (Exception e) {
                        logger.error("更新排除工具列表失败", e);
                        throw new RuntimeException("更新失败", e);
                    }
                }
            }
        } else {
            // 禁用工具：添加到排除列表
            if (existingConfigs.isEmpty()) {
                // 创建新配置
                SysMcpToolExclude newConfig = new SysMcpToolExclude();
                newConfig.setExcludeType("global");
                newConfig.setBindType(bindType);
                newConfig.setBindCode(bindCode);
                newConfig.setBindKey(bindKey);
                try {
                    newConfig.setExcludeTools(objectMapper.writeValueAsString(Arrays.asList(toolName)));
                    newConfig.setCreateTime(LocalDateTime.now());
                    newConfig.setUpdateTime(LocalDateTime.now());
                    mcpToolExcludeMapper.add(newConfig);
                } catch (Exception e) {
                    logger.error("创建排除工具配置失败", e);
                    throw new RuntimeException("创建失败", e);
                }
            } else {
                // 更新现有配置
                SysMcpToolExclude config = existingConfigs.get(0);
                List<String> excludeList = parseExcludeTools(config.getExcludeTools());
                if (!excludeList.contains(toolName)) {
                    excludeList.add(toolName);
                    try {
                        config.setExcludeTools(objectMapper.writeValueAsString(excludeList));
                        config.setUpdateTime(LocalDateTime.now());
                        mcpToolExcludeMapper.update(config);
                    } catch (Exception e) {
                        logger.error("更新排除工具列表失败", e);
                        throw new RuntimeException("更新失败", e);
                    }
                }
            }
        }
    }
    
    @Override
    @Cacheable(value = CACHE_NAME, key = "'role_disabled:' + #roleId")
    public List<String> getRoleDisabledTools(Integer roleId) {
        List<String> allDisabled = new ArrayList<>();
        
        // 查询所有角色的禁用工具配置
        List<SysMcpToolExclude> configs = mcpToolExcludeMapper.selectByCondition(
            "role", null, null, roleId.toString());
        
        for (SysMcpToolExclude config : configs) {
            allDisabled.addAll(parseExcludeTools(config.getExcludeTools()));
        }
        
        return allDisabled;
    }
    
    @Override
    @Cacheable(value = CACHE_NAME, key = "'global_disabled'")
    public List<String> getGlobalDisabledTools() {
        List<String> allDisabled = new ArrayList<>();
        
        // 查询所有全局禁用工具配置
        List<SysMcpToolExclude> configs = mcpToolExcludeMapper.selectByCondition(
            "global", null, null, "0");
        
        for (SysMcpToolExclude config : configs) {
            allDisabled.addAll(parseExcludeTools(config.getExcludeTools()));
        }
        
        return allDisabled;
    }
    
    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void refreshCache() {
        logger.info("MCP工具排除缓存已刷新");
    }
    
    /**
     * 获取全局排除的工具
     */
    @Cacheable(value = CACHE_NAME, key = "'global_excluded'")
    private Set<String> getGlobalExcludedTools() {
        Set<String> tools = new HashSet<>();
        
        // 查询所有全局排除工具配置
        List<SysMcpToolExclude> configs = mcpToolExcludeMapper.selectByCondition(
            "global", null, null, "0");
        
        for (SysMcpToolExclude config : configs) {
            tools.addAll(parseExcludeTools(config.getExcludeTools()));
        }
        
        return tools;
    }
    
    /**
     * 获取角色排除的工具
     */
    @Cacheable(value = CACHE_NAME, key = "'role_excluded:' + #roleId")
    private Set<String> getRoleExcludedTools(Integer roleId) {
        Set<String> tools = new HashSet<>();
        
        // 查询所有角色排除工具配置
        List<SysMcpToolExclude> configs = mcpToolExcludeMapper.selectByCondition(
            "role", null, null, roleId.toString());
        
        for (SysMcpToolExclude config : configs) {
            tools.addAll(parseExcludeTools(config.getExcludeTools()));
        }
        
        return tools;
    }
    
    /**
     * 解析排除工具JSON字符串
     */
    private List<String> parseExcludeTools(String excludeToolsJson) {
        try {
            if (!StringUtils.hasText(excludeToolsJson)) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(excludeToolsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            logger.error("解析排除工具JSON失败: {}", excludeToolsJson, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> getSystemGlobalTools() {
        // 返回系统全局工具列表
        return Arrays.asList(
            "func_playMusic",      // 播放音乐
            "func_changeRole",     // 切换角色
            "func_playHuiBen",     // 播放绘本
            "func_new_chat",   // 新对话
            "func_exitSession"     // 会话退出
        );
    }
    
    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void batchSetRoleExcludeTools(Integer roleId, List<String> excludeTools, String serverName) {
        String bindType = "mcp_server";
        String bindCode = serverName != null ? serverName : "XiaoZhi_MCP_Client"; // 默认服务器名称
        String bindKey = roleId.toString();
        
        // 查询现有配置
        List<SysMcpToolExclude> existingConfigs = mcpToolExcludeMapper.selectByCondition(
            "role", bindType, bindCode, bindKey);
        
        if (excludeTools == null || excludeTools.isEmpty()) {
            // 如果排除列表为空，删除现有配置
            if (!existingConfigs.isEmpty()) {
                mcpToolExcludeMapper.delete(existingConfigs.get(0).getId());
            }
        } else {
            try {
                String excludeToolsJson = objectMapper.writeValueAsString(excludeTools);
                
                if (existingConfigs.isEmpty()) {
                    // 创建新配置
                    SysMcpToolExclude newConfig = new SysMcpToolExclude();
                    newConfig.setExcludeType("role");
                    newConfig.setBindType(bindType);
                    newConfig.setBindCode(bindCode);
                    newConfig.setBindKey(bindKey);
                    newConfig.setExcludeTools(excludeToolsJson);
                    newConfig.setCreateTime(LocalDateTime.now());
                    newConfig.setUpdateTime(LocalDateTime.now());
                    mcpToolExcludeMapper.add(newConfig);
                } else {
                    // 更新现有配置
                    SysMcpToolExclude config = existingConfigs.get(0);
                    config.setExcludeTools(excludeToolsJson);
                    config.setUpdateTime(LocalDateTime.now());
                    mcpToolExcludeMapper.update(config);
                }
            } catch (Exception e) {
                logger.error("批量设置角色排除工具失败", e);
                throw new RuntimeException("批量设置失败", e);
            }
        }
    }
}
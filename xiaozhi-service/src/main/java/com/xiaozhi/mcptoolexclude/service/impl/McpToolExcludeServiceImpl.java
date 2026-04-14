package com.xiaozhi.mcptoolexclude.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.mcptoolexclude.dal.mysql.dataobject.McpToolExcludeDO;
import com.xiaozhi.mcptoolexclude.dal.mysql.mapper.McpToolExcludeMapper;
import com.xiaozhi.mcptoolexclude.service.McpToolExcludeService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class McpToolExcludeServiceImpl implements McpToolExcludeService {

    private static final Logger logger = LoggerFactory.getLogger(McpToolExcludeServiceImpl.class);
    private static final String EXCLUDE_TYPE_GLOBAL = "global";
    private static final String EXCLUDE_TYPE_ROLE = "role";
    private static final String BIND_TYPE_MCP_SERVER = "mcp_server";
    private static final String GLOBAL_BIND_KEY = "0";
    private static final String DEFAULT_SERVER_NAME = "XiaoZhi_MCP_Client";

    @Resource
    private McpToolExcludeMapper mcpToolExcludeMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Cacheable(value = CACHE_NAME, key = "'excluded_tools:' + #roleId")
    public Set<String> getExcludedTools(Integer roleId) {
        Set<String> excludedTools = new LinkedHashSet<>(getGlobalDisabledTools());
        if (roleId != null) {
            excludedTools.addAll(getRoleDisabledTools(roleId));
        }
        return excludedTools;
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void toggleRoleToolStatus(Integer roleId, String toolName, String serverName, boolean enabled) {
        toggleToolStatus(EXCLUDE_TYPE_ROLE, serverName, String.valueOf(roleId), toolName, enabled);
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void toggleGlobalToolStatus(String toolName, String serverName, boolean enabled) {
        toggleToolStatus(EXCLUDE_TYPE_GLOBAL, serverName, GLOBAL_BIND_KEY, toolName, enabled);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'role_disabled:' + #roleId")
    public List<String> getRoleDisabledTools(Integer roleId) {
        if (roleId == null) {
            return List.of();
        }
        List<String> disabledTools = new ArrayList<>();
        List<McpToolExcludeDO> configs = mcpToolExcludeMapper.selectList(new LambdaQueryWrapper<McpToolExcludeDO>()
            .eq(McpToolExcludeDO::getExcludeType, EXCLUDE_TYPE_ROLE)
            .eq(McpToolExcludeDO::getBindKey, String.valueOf(roleId))
            .orderByDesc(McpToolExcludeDO::getCreateTime));
        for (McpToolExcludeDO config : configs) {
            disabledTools.addAll(parseExcludeTools(config.getExcludeTools()));
        }
        return disabledTools;
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'global_disabled'")
    public List<String> getGlobalDisabledTools() {
        List<String> disabledTools = new ArrayList<>();
        List<McpToolExcludeDO> configs = mcpToolExcludeMapper.selectList(new LambdaQueryWrapper<McpToolExcludeDO>()
            .eq(McpToolExcludeDO::getExcludeType, EXCLUDE_TYPE_GLOBAL)
            .eq(McpToolExcludeDO::getBindKey, GLOBAL_BIND_KEY)
            .orderByDesc(McpToolExcludeDO::getCreateTime));
        for (McpToolExcludeDO config : configs) {
            disabledTools.addAll(parseExcludeTools(config.getExcludeTools()));
        }
        return disabledTools;
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void batchSetRoleExcludeTools(Integer roleId, List<String> excludeTools, String serverName) {
        if (roleId == null) {
            return;
        }
        String bindCode = StringUtils.hasText(serverName) ? serverName : DEFAULT_SERVER_NAME;
        McpToolExcludeDO config = getConfig(EXCLUDE_TYPE_ROLE, BIND_TYPE_MCP_SERVER, bindCode, String.valueOf(roleId));
        if (excludeTools == null || excludeTools.isEmpty()) {
            if (config != null && config.getId() != null) {
                mcpToolExcludeMapper.deleteById(config.getId());
            }
            return;
        }
        saveConfig(config, EXCLUDE_TYPE_ROLE, bindCode, String.valueOf(roleId), excludeTools);
    }

    private void toggleToolStatus(String excludeType, String serverName, String bindKey, String toolName, boolean enabled) {
        if (!StringUtils.hasText(bindKey) || !StringUtils.hasText(toolName)) {
            return;
        }
        String bindCode = StringUtils.hasText(serverName) ? serverName : DEFAULT_SERVER_NAME;
        McpToolExcludeDO config = getConfig(excludeType, BIND_TYPE_MCP_SERVER, bindCode, bindKey);
        List<String> excludeTools = config == null ? new ArrayList<>() : parseExcludeTools(config.getExcludeTools());
        if (enabled) {
            excludeTools.remove(toolName);
        } else if (!excludeTools.contains(toolName)) {
            excludeTools.add(toolName);
        }

        if (excludeTools.isEmpty()) {
            if (config != null && config.getId() != null) {
                mcpToolExcludeMapper.deleteById(config.getId());
            }
            return;
        }
        saveConfig(config, excludeType, bindCode, bindKey, excludeTools);
    }

    private McpToolExcludeDO getConfig(String excludeType, String bindType, String bindCode, String bindKey) {
        return mcpToolExcludeMapper.selectOne(new LambdaQueryWrapper<McpToolExcludeDO>()
            .eq(McpToolExcludeDO::getExcludeType, excludeType)
            .eq(McpToolExcludeDO::getBindType, bindType)
            .eq(McpToolExcludeDO::getBindCode, bindCode)
            .eq(McpToolExcludeDO::getBindKey, bindKey)
            .last("LIMIT 1"));
    }

    private void saveConfig(McpToolExcludeDO config, String excludeType, String bindCode, String bindKey, List<String> excludeTools) {
        try {
            String excludeToolsJson = objectMapper.writeValueAsString(excludeTools);
            McpToolExcludeDO target = config == null ? new McpToolExcludeDO() : config;
            target.setExcludeType(excludeType);
            target.setBindType(BIND_TYPE_MCP_SERVER);
            target.setBindCode(bindCode);
            target.setBindKey(bindKey);
            target.setExcludeTools(excludeToolsJson);
            if (target.getId() == null) {
                mcpToolExcludeMapper.insert(target);
            } else {
                mcpToolExcludeMapper.updateById(target);
            }
        } catch (Exception e) {
            logger.error("Save MCP tool exclude config failed", e);
            throw new OperationFailedException("保存MCP工具排除配置失败", e);
        }
    }

    private List<String> parseExcludeTools(String excludeToolsJson) {
        try {
            if (!StringUtils.hasText(excludeToolsJson)) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(excludeToolsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            logger.error("Parse MCP tool exclude json failed: {}", excludeToolsJson, e);
            return new ArrayList<>();
        }
    }
}

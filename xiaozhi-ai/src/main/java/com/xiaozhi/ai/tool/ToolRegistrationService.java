package com.xiaozhi.ai.tool;

import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.mcptoolexclude.service.McpToolExcludeService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 工具注册协调器，统一编排工具注册。
 * <p>
 * 职责：
 * 1. 获取当前会话的排除工具列表（全局 + 角色级别）
 * 2. 依次调用所有注入的 {@link ToolRegistrar} 实现
 */
@Service
public class ToolRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistrationService.class);

    @Resource
    private McpToolExcludeService mcpToolExcludeService;

    /**
     * 所有 ToolRegistrar 实现，Spring 自动收集
     */
    @Resource
    private List<ToolRegistrar> registrars;

    /**
     * 向会话注册所有可用工具
     *
     * @param toolSession 当前设备会话
     */
    public void register(ToolSession toolSession) {
        Integer roleId = toolSession.getRoleId();
        Set<String> excludedTools = mcpToolExcludeService.getExcludedTools(roleId);

        for (ToolRegistrar registrar : registrars) {
            try {
                registrar.register(toolSession, excludedTools);
            } catch (Exception e) {
                logger.warn("ToolRegistrar {} 注册失败", registrar.getClass().getSimpleName(), e);
            }
        }

    }

}

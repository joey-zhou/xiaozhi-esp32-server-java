package com.xiaozhi.ai.mcp.registrar;

import com.xiaozhi.ai.tool.ToolRegistrar;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.ai.tool.session.ToolSession;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 系统全局工具注册器。
 * 注册 {@link ToolsGlobalRegistry#getAllFunctions(ToolSession)} 中当前启用（未注释 @Component）的系统全局工具，
 * 具体清单随 xiaozhi-dialogue.llm.tool.function 下各实现类的启停状态变化。
 *
 */
@Component
@Order(3)
public class SystemToolRegistrar implements ToolRegistrar {

    @Resource
    private ToolsGlobalRegistry toolsGlobalRegistry;

    @Override
    public void register(ToolSession toolSession, Set<String> excludedTools) {
        ToolsSessionHolder functionSessionHolder = toolSession.getToolsSessionHolder();
        Map<String, ToolCallback> globalFunctions = toolsGlobalRegistry.getAllFunctions(toolSession);

        globalFunctions.forEach((toolName, toolCallback) -> {
            if (!excludedTools.contains(toolName)) {
                functionSessionHolder.registerFunction(toolName, toolCallback);
            }
        });
    }
}

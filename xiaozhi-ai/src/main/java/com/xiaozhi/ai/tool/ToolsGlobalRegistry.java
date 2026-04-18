package com.xiaozhi.ai.tool;

import com.xiaozhi.ai.tool.session.ToolSession;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ToolsGlobalRegistry implements ToolCallbackResolver {
    private static final String TAG = "FUNCTION_GLOBAL";

    // 用于存储所有function列表
    protected static final ConcurrentHashMap<String, ToolCallback> allFunction
            = new ConcurrentHashMap<>();

    @Autowired(required = false)
    protected List<GlobalFunction> globalFunctions = List.of();

    @Override
    public ToolCallback resolve(@NotNull String toolName) {
        return allFunction.get(toolName);
    }

    /**
     * Register a function by name
     *
     * @param name the name of the function to register
     * @return the registered function or null if not found
     */
    public ToolCallback registerFunction(String name, ToolCallback functionCallTool) {
        ToolCallback result = allFunction.putIfAbsent(name, functionCallTool);
        return result;
    }

    /**
     * Unregister a function by name
     *
     * @param name the name of the function to unregister
     * @return true if successful, false otherwise
     */
    public boolean unregisterFunction(String name) {
        // Check if the function exists before unregistering
        if (!allFunction.containsKey(name)) {
            return false;
        }
        allFunction.remove(name);
        return true;
    }

    /**
     * Get all registered functions
     *
     * @return a map of all registered functions
     */
    public Map<String, ToolCallback> getAllFunctions(ToolSession toolSession) {
        // 注意：这里不再自动注册所有全局函数到allFunction中
        // 而是返回一个临时的Map，由 ToolRegistrationService 统一管理工具注册
        Map<String, ToolCallback> tempFunctions = new HashMap<>();
        globalFunctions.forEach(
                globalFunction -> {
                    ToolCallback toolCallback = globalFunction.getFunctionCallTool(toolSession);
                    if(toolCallback != null){
                        tempFunctions.put(toolCallback.getToolDefinition().name(), toolCallback);
                    }
                }
        );
        return tempFunctions;
    }

    /**
     * 获取所有已注册 GlobalFunction 的工具摘要（name + description）
     */
    public List<Map<String, String>> getGlobalToolSummaries() {
        return globalFunctions.stream()
                .map(f -> Map.of("name", f.getToolName(), "description", f.getToolDescription()))
                .toList();
    }

    public interface GlobalFunction{
        ToolCallback getFunctionCallTool(ToolSession toolSession);

        /**
         * 工具名称
         */
        String getToolName();

        /**
         * 工具描述
         */
        String getToolDescription();
    }
}

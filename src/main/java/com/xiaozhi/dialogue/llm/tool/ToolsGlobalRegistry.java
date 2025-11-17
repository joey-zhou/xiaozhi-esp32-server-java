package com.xiaozhi.dialogue.llm.tool;

import com.xiaozhi.communication.common.ChatSession;
import jakarta.annotation.Resource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ToolsGlobalRegistry implements ToolCallbackResolver {
    private final Logger logger = LoggerFactory.getLogger(ToolsGlobalRegistry.class);
    private static final String TAG = "FUNCTION_GLOBAL";

    // 用于存储所有function列表
    protected static final ConcurrentHashMap<String, ToolCallback> allFunction
            = new ConcurrentHashMap<>();

    @Resource
    protected List<GlobalFunction> globalFunctions;

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
    public Map<String, ToolCallback> getAllFunctions(ChatSession chatSession) {
        // 注意：这里不再自动注册所有全局函数到allFunction中
        // 而是返回一个临时的Map，由McpSessionManager统一管理工具注册
        Map<String, ToolCallback> tempFunctions = new HashMap<>();
        globalFunctions.forEach(
                globalFunction -> {
                    ToolCallback toolCallback = globalFunction.getFunctionCallTool(chatSession);
                    if(toolCallback != null){
                        tempFunctions.put(toolCallback.getToolDefinition().name(), toolCallback);
                    }
                }
        );
        return tempFunctions;
    }

    public interface GlobalFunction{
        ToolCallback getFunctionCallTool(ChatSession chatSession);
    }
}

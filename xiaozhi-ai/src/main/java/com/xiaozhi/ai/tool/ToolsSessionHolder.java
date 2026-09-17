package com.xiaozhi.ai.tool;

import com.xiaozhi.common.model.bo.DeviceBO;
import org.springframework.ai.tool.ToolCallback;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
/**
 * 与session绑定的functionTools
 */
@Slf4j
public class ToolsSessionHolder {
    private static final String TAG = "FUNCTION_SESSION";

    /** 设备 MCP 注册线程、IoT 消息线程与对话线程并发读写，必须是并发容器 */
    private final Map<String, ToolCallback> functionRegistry = new ConcurrentHashMap<>();

    private String sessionId;

    private ToolsGlobalRegistry globalFunctionRegistry;

    private DeviceBO device;

    public ToolsSessionHolder(String sessionId, DeviceBO device, ToolsGlobalRegistry globalFunctionRegistry) {
        this.sessionId = sessionId;
        this.device = device;
        this.globalFunctionRegistry = globalFunctionRegistry;
    }

    /**
     * Register a function by name
     *
     * @param name the name of the function to register
     */
    public void registerFunction(String name, ToolCallback functionCallTool) {
        ToolCallback previous = functionRegistry.put(name, functionCallTool);
        if (previous != null && previous != functionCallTool) {
            log.warn("[{}] - SessionId:{} Function:{} 重名注册，旧实例被静默覆盖", TAG, sessionId, name);
        }
    }

    /**
     * Unregister a function by name
     *
     * @param name the name of the function to unregister
     * @return true if successful, false otherwise
     */
    public boolean unregisterFunction(String name) {
        if (functionRegistry.remove(name) == null) {
            log.error("[{}] - SessionId:{} Function:{} not found", TAG, sessionId, name);
            return false;
        }
        functionRegistry.remove(name);
        log.info("[{}] - SessionId:{} Function:{} unregistered successfully", TAG, sessionId, name);
        return true;
    }

    /**
     * Get a function by name
     *
     * @param name the name of the function to retrieve
     * @return the function or null if not found
     */
    public ToolCallback getFunction(String name) {
        return functionRegistry.get(name);
    }

    /**
     * Get all registered functions
     *
     * @return a map of all registered functions
     */
    public List<ToolCallback> getAllFunction() {
        return List.copyOf(functionRegistry.values());
    }

    /**
     * Get all registered functions name
     *
     * @return a list of all registered function name
     */
    public List<String> getAllFunctionName() {
        return List.copyOf(functionRegistry.keySet());
    }

    /**
     * 注册全局函数到FunctionHolder
     */
    public void registerGlobalFunctionTools() {
        // 全局函数由 ToolRegistrationService 统一管理
        log.debug("[{}] - SessionId:{} 跳过自动注册全局函数，由 ToolRegistrationService 统一管理", TAG, sessionId);
    }
}

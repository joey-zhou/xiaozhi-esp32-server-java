package com.xiaozhi.dialogue.llm.tool;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.entity.SysDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;

import java.util.*;

/**
 * 与session绑定的functionTools
 */
public class ToolsSessionHolder {
    private final Logger logger = LoggerFactory.getLogger(ToolsSessionHolder.class);

    private static final String TAG = "FUNCTION_SESSION";

    private final Map<String, ToolCallback> functionRegistry = new HashMap<>();

    private String sessionId;

    private ToolsGlobalRegistry globalFunctionRegistry;

    private SysDevice sysDevice;

    public ToolsSessionHolder(String sessionId, SysDevice sysDevice, ToolsGlobalRegistry globalFunctionRegistry) {
        this.sessionId = sessionId;
        this.sysDevice = sysDevice;
        this.globalFunctionRegistry = globalFunctionRegistry;
    }

    /**
     * Register a global function by name
     *
     * @param name the name of the function to register
     * @return the registered function or null if not found
     */
    public ToolCallback registerFunction(String name) {
        // Look up the function in the globalFunctionRegistry
        ToolCallback func = globalFunctionRegistry.resolve(name);
        if (func == null) {
            logger.error("[{}] - SessionId:{} Function:{} not found in globalFunctionRegistry", TAG, sessionId, name);
            return null;
        }
        functionRegistry.put(name, func);
        logger.debug("[{}] - SessionId:{} Function:{} registered from global successfully", TAG, sessionId, name);
        return func;
    }

    /**
     * Register a function by name
     *
     * @param name the name of the function to register
     * @return the registered function or null if not found
     */
    public void registerFunction(String name, ToolCallback functionCallTool) {
        functionRegistry.put(name, functionCallTool);
//        logger.debug("[{}] - SessionId:{} Function:{} registered successfully", TAG, sessionId, name);
    }

    /**
     * Unregister a function by name
     *
     * @param name the name of the function to unregister
     * @return true if successful, false otherwise
     */
    public boolean unregisterFunction(String name) {
        // Check if the function exists before unregistering
        if (!functionRegistry.containsKey(name)) {
            logger.error("[{}] - SessionId:{} Function:{} not found", TAG, sessionId, name);
            return false;
        }
        functionRegistry.remove(name);
        logger.info("[{}] - SessionId:{} Function:{} unregistered successfully", TAG, sessionId, name);
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
        return functionRegistry.values().stream().toList();
    }

    /**
     * Get all registered functions name
     *
     * @return a list of all registered function name
     */
    public List<String> getAllFunctionName() {
        return new ArrayList<>(functionRegistry.keySet());
    }

    /**
     * 注册全局函数到FunctionHolder
     */
    public void registerGlobalFunctionTools(ChatSession chatSession) {
        String functionNames = sysDevice == null? null : sysDevice.getFunctionNames();
        if(functionNames != null && !functionNames.isEmpty()){//如果指定了function配置，则只加载指定的
            String[] functionNameArr = functionNames.split(",");
            for(String functionName : functionNameArr){
                registerFunction(functionName);
            }
        }else{//否则加载所有的全局function
            // 注意：这里不再自动注册所有全局函数，而是由McpSessionManager统一管理
            // globalFunctionRegistry.getAllFunctions(chatSession).forEach(this::registerFunction);
            logger.debug("[{}] - SessionId:{} 跳过自动注册全局函数，由McpSessionManager统一管理", TAG, sessionId);
        }
    }
}

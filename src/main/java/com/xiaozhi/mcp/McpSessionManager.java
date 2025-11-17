package com.xiaozhi.mcp;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * MCP会话管理器
 */
@Service
public class McpSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(McpSessionManager.class);

    @Autowired
    private ToolsGlobalRegistry toolsGlobalRegistry;

    public void customMcpHandler(ChatSession chatSession) {
        //注册MCP接入点的方法
        ToolsSessionHolder functionSessionHolder = chatSession.getFunctionSessionHolder();
        
        // 注册系统全局工具
        Map<String, ToolCallback> globalFunctions = toolsGlobalRegistry.getAllFunctions(chatSession);
        
        globalFunctions.forEach((toolName, toolCallback) -> {
            functionSessionHolder.registerFunction(toolName, toolCallback);
        });

    }
    
}
package com.xiaozhi.ai.mcp.server;

import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具查询服务实现。
 * 委托 ToolsGlobalRegistry 获取全局工具元数据。
 */
@Service
public class McpToolQueryServiceImpl implements McpToolQueryService {

    @Resource
    private ToolsGlobalRegistry toolsGlobalRegistry;

    @Override
    public List<Map<String, String>> getSystemGlobalToolSummaries() {
        return toolsGlobalRegistry.getGlobalToolSummaries();
    }
}

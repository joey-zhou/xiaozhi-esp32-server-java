package com.xiaozhi.ai.mcp.server;

/**
 * MCP 工具信息
 */
public class McpToolInfo {

    private final String serverCode;
    private final ToolDefinition definition;

    public McpToolInfo(String serverCode, ToolDefinition definition) {
        this.serverCode = serverCode;
        this.definition = definition;
    }

    public String getServerCode() {
        return serverCode;
    }

    public ToolDefinition getDefinition() {
        return definition;
    }

    public record ToolDefinition(String name, String description, Object inputSchema) {
    }
}

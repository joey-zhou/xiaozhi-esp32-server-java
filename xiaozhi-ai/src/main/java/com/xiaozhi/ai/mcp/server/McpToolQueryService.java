package com.xiaozhi.ai.mcp.server;

import com.xiaozhi.common.model.resp.McpToolSummaryResp;

import java.util.List;

/**
 * MCP 工具查询服务。
 * 提供 MCP Server 工具列表查询和系统全局工具元数据查询。
 */
public interface McpToolQueryService {


    /**
     * 获取系统全局内置工具摘要（name + description）
     */
    List<McpToolSummaryResp> getSystemGlobalToolSummaries();
}

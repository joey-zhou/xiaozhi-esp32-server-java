package com.xiaozhi.ai.tool;

import com.xiaozhi.ai.tool.session.ToolSession;

import java.util.Set;

/**
 * 工具注册器接口。
 * 每种工具来源对应一个实现：系统全局工具、设备端MCP工具、远程MCP Server工具、本地MCP Endpoint工具。
 * Spring 自动收集注入。
 */
public interface ToolRegistrar {

    /**
     * 向会话注册工具
     *
     * @param toolSession   当前设备会话
     * @param excludedTools 需排除的工具名称集合
     */
    void register(ToolSession toolSession, Set<String> excludedTools);
}

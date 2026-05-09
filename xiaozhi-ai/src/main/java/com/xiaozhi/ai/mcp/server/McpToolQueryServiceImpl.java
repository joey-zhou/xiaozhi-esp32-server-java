package com.xiaozhi.ai.mcp.server;

import com.xiaozhi.ai.tool.GlobalToolRedisRegistry;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
/**
 * MCP 工具查询服务实现。
 * 委托 ToolsGlobalRegistry 获取全局工具元数据。
 */
@Slf4j
@Service
public class McpToolQueryServiceImpl implements McpToolQueryService {

    @Resource
    private ToolsGlobalRegistry toolsGlobalRegistry;

    @Autowired(required = false)
    private GlobalToolRedisRegistry globalToolRedisRegistry;

    @Override
    public List<Map<String, String>> getSystemGlobalToolSummaries() {
        // 优先使用本进程已注册的 GlobalFunction（dialogue 进程 / 单体部署）
        List<Map<String, String>> inMemory = toolsGlobalRegistry.getGlobalToolSummaries();
        if (!inMemory.isEmpty()) {
            return inMemory;
        }
        // 回退到 Redis 共享注册表（server 进程跨进程读取 dialogue 发布的元数据）
        if (globalToolRedisRegistry == null) {
            return inMemory;
        }
        return globalToolRedisRegistry.getAll().stream()
                .map(t -> Map.of("name", t.getName(), "description", t.getDescription()))
                .toList();
    }
}

package com.xiaozhi.dialogue.llm.tool.mcp.device;

import com.xiaozhi.communication.domain.DeviceMcpMessage;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备端mcp相关属性
 */
@Data
public class DeviceMcpHolder {
    /**
     * mcp请求ID
     */
    private final AtomicLong mcpRequestId = new AtomicLong(10000L);
    /**
     * mcp初始化完成
     */
    private boolean mcpInitialized = false;
    /**
     * mcp指令阻塞请求表
     */
    private Map<Long, CompletableFuture<DeviceMcpMessage>> mcpPendingRequests = new HashMap<>();
    /**
     * mcp工具获取游标 用于分页，首次请求为空字符串
     */
    private String mcpCursor = "";

    public Long getMcpRequestId() {
        return mcpRequestId.getAndIncrement();
    }
}
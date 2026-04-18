package com.xiaozhi.ai.mcp.registrar;

import com.xiaozhi.ai.tool.ToolRegistrar;
import com.xiaozhi.ai.tool.session.ToolSession;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;
/**
 * 设备端 MCP 工具注册器。
 * 设备端MCP工具在设备初始化时已通过 DeviceMcpService.initialize() 注册到 toolSession，
 * 此处仅作占位，日志记录已注册状态。
 */
@Slf4j
@Component
@Order(1)
public class DeviceMcpToolRegistrar implements ToolRegistrar {

    @Override
    public void register(ToolSession toolSession, Set<String> excludedTools) {
        if (toolSession.isDeviceMcpInitialized()) {
            log.debug("SessionId: {}, 设备端MCP工具已初始化", toolSession.getSessionId());
        }
    }
}

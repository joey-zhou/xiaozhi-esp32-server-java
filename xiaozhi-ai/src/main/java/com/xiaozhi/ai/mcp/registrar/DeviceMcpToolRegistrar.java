package com.xiaozhi.ai.mcp.registrar;

import com.xiaozhi.ai.tool.ToolRegistrar;
import com.xiaozhi.ai.tool.session.ToolSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 设备端 MCP 工具注册器。
 * 设备端MCP工具在设备初始化时已通过 DeviceMcpService.initialize() 注册到 toolSession，
 * 此处仅作占位，日志记录已注册状态。
 */
@Component
@Order(1)
public class DeviceMcpToolRegistrar implements ToolRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(DeviceMcpToolRegistrar.class);

    @Override
    public void register(ToolSession toolSession, Set<String> excludedTools) {
        if (toolSession.isDeviceMcpInitialized()) {
            logger.debug("SessionId: {}, 设备端MCP工具已初始化", toolSession.getSessionId());
        }
    }
}

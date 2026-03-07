package com.xiaozhi.communication.domain;

import com.xiaozhi.communication.domain.mcp.device.DeviceMcpPayload;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP请求类，用于处理向设备发送相关的MCP请求
 */
@Data

public final  class DeviceMcpMessage extends Message {
    public DeviceMcpMessage() {
        super("mcp");
    }

    private String sessionId;//会话id
    private String type = "mcp";
    private DeviceMcpPayload payload;
}
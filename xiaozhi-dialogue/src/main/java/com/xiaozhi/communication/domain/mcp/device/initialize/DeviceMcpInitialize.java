package com.xiaozhi.communication.domain.mcp.device.initialize;

import lombok.Data;

import java.util.Collections;
import java.util.Map;

@Data
public class DeviceMcpInitialize {
    private String protocolVersion = "2024-11-05";
    private Map<String, Object>  capabilities = Collections.emptyMap();
    private DeviceMcpClientInfo clientInfo = new DeviceMcpClientInfo();
}
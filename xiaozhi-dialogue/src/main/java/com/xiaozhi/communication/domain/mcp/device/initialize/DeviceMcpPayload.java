package com.xiaozhi.communication.domain.mcp.device.initialize;

import lombok.Data;

import java.util.Map;

@Data
public class DeviceMcpPayload {
    private String jsonrpc = "2.0";
    private String method;//方法名称
    private Object params;
    private Long id;//请求id
    private Map<String, Object> result;//请求结果
    private Map<String, Object> error;//请求失败信息

}
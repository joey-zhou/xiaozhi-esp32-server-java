package com.xiaozhi.communication.domain;

import lombok.Data;

@Data
public class HelloFeatures {
    /**
     * 设备是否启用mcp
     */
    private Boolean mcp = false;
    /**
     * 设备是否启用服务端aec
     */
    private Boolean aec = false;
}
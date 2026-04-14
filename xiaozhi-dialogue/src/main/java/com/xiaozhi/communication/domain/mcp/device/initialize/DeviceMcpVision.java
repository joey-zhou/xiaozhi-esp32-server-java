package com.xiaozhi.communication.domain.mcp.device.initialize;

import lombok.Data;

/**
 * 摄像头视觉相关
 */
@Data
public class DeviceMcpVision {
    private String url;//摄像头: 图片处理地址(必须是http地址, 不是websocket地址)
    private String token;// url toke
}
package com.xiaozhi.communication.registry;

import lombok.Data;

import java.io.Serializable;

/**
 * Dialogue服务器信息 — 用于服务注册/发现
 */
@Data
public class DialogueServerInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String instanceId;
    private String websocketAddress;
    private String udpAddress;
    private String otaAddress;
    private String mcpAddress;
    private String serverAddress;
    private long lastHeartbeat;
}

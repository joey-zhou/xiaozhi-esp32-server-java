package com.xiaozhi.communication.registry;

import java.io.Serializable;

/**
 * Dialogue服务器信息 — 用于服务注册/发现
 */
public class DialogueServerInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String instanceId;
    private String websocketAddress;
    private String udpAddress;
    private String otaAddress;
    private String mcpAddress;
    private String serverAddress;
    private long lastHeartbeat;

    public DialogueServerInfo() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getWebsocketAddress() {
        return websocketAddress;
    }

    public void setWebsocketAddress(String websocketAddress) {
        this.websocketAddress = websocketAddress;
    }

    public String getUdpAddress() {
        return udpAddress;
    }

    public void setUdpAddress(String udpAddress) {
        this.udpAddress = udpAddress;
    }

    public String getOtaAddress() {
        return otaAddress;
    }

    public void setOtaAddress(String otaAddress) {
        this.otaAddress = otaAddress;
    }

    public String getMcpAddress() {
        return mcpAddress;
    }

    public void setMcpAddress(String mcpAddress) {
        this.mcpAddress = mcpAddress;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }
}

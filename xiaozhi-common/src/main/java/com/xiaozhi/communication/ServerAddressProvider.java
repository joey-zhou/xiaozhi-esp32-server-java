package com.xiaozhi.communication;

import com.xiaozhi.utils.CmsUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 服务地址提供器 — 负责组装各协议的访问地址
 */
@Component
public class ServerAddressProvider {

    /** WebSocket 路径，与 WebSocketConfig.WS_PATH 保持一致 */
    public static final String WS_PATH = "/ws/xiaozhi/v1/";

    private String websocketAddress;
    private String otaAddress;
    private String udpAddress;
    private String mcpAddress;
    private String serverAddress;

    @Resource
    private CmsUtils cmsUtils;

    @Value("${udp.server.port:1884}")
    private int udpPort;

    @Value("${xiaozhi.server.port:8091}")
    private int serverPort;

    @Value("${xiaozhi.dialogue.port:8092}")
    private int dialoguePort;

    @Value("${xiaozhi.server.domain:}")
    private String domain;

    @PostConstruct
    private void initializeAddresses() {
        if (domain != null && !domain.isEmpty()) {
            udpAddress = "udp." + domain;
            websocketAddress = "wss://ws." + domain + WS_PATH;
            mcpAddress = "wss://mcp." + domain + "/ws/mcp/";
            otaAddress = "https://" + domain + "/api/device/ota";
            serverAddress = "https://" + domain;
        } else {
            String serverIp = cmsUtils.getServerIp();
            udpAddress = serverIp;
            websocketAddress = "ws://" + serverIp + ":" + dialoguePort + WS_PATH;
            mcpAddress = "ws://" + serverIp + ":" + dialoguePort + "/ws/mcp/";
            otaAddress = "http://" + serverIp + ":" + serverPort + "/api/device/ota";
            serverAddress = "http://" + serverIp + ":" + serverPort;
        }
    }

    public String getUdpAddress() {
        return udpAddress;
    }

    public String getWebsocketAddress() {
        return websocketAddress;
    }

    public String getOtaAddress() {
        return otaAddress;
    }

    public String getMcpAddress() {
        return mcpAddress;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public String getServerIp() {
        return cmsUtils.getServerIp();
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getDialoguePort() {
        return dialoguePort;
    }
}

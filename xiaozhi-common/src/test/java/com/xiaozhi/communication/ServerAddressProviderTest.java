package com.xiaozhi.communication;

import com.xiaozhi.utils.ServerIpProbe;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * server 进程在注册表里找不到对话实例时按这份地址给设备下发连接，
 * 设备 WebSocket 在 dialogue 的端口上，不能拼 server 自己的端口
 */
class ServerAddressProviderTest {

    @Test
    void websocketAddressPointsAtTheDialoguePortFromTheServerProcess() {
        ServerAddressProvider provider = provider(9091, 9092);

        assertThat(provider.getWebsocketAddress()).isEqualTo("ws://10.0.0.2:9092/ws/xiaozhi/v1/");
        assertThat(provider.getOtaAddress()).isEqualTo("http://10.0.0.2:9091/api/device/ota");
    }

    private static ServerAddressProvider provider(int serverPort, int dialoguePort) {
        ServerIpProbe serverIpProbe = mock(ServerIpProbe.class);
        when(serverIpProbe.getServerIp()).thenReturn("10.0.0.2");
        ServerAddressProvider provider = new ServerAddressProvider();
        ReflectionTestUtils.setField(provider, "serverIpProbe", serverIpProbe);
        ReflectionTestUtils.setField(provider, "serverPort", serverPort);
        ReflectionTestUtils.setField(provider, "udpPort", 1884);
        ReflectionTestUtils.setField(provider, "dialoguePort", dialoguePort);
        ReflectionTestUtils.setField(provider, "domain", "");
        ReflectionTestUtils.invokeMethod(provider, "initializeAddresses");
        return provider;
    }
}

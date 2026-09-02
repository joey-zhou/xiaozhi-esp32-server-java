package com.xiaozhi.communication.common;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * 钉住 deviceId → sessionId 反向索引的归属：removeSession 只能清掉自己建立的那条映射。
 *
 * <p>设备断线重连时新会话已经 registerDevice 建好新映射，旧连接的 afterConnectionClosed 才姗姗来迟；
 * 若按 deviceId 无条件删除，此后 getSessionByDeviceId 恒为 null，消息推送、角色变更广播、
 * 跨实例幽灵会话清理、不活跃扫描对该设备全部失效，线上表现为“设备在线但推不动”。
 */
@ExtendWith(MockitoExtension.class)
class SessionManagerDeviceIndexTest {

    private static final String DEVICE_ID = "aa:bb:cc:11:22:33";

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private DeviceRegistry deviceRegistry;
    @Mock
    private org.springframework.web.socket.WebSocketSession staleSpringSession;
    @Mock
    private org.springframework.web.socket.WebSocketSession freshSpringSession;

    private SessionManager sessionManager;
    private WebSocketSession staleSession;
    private WebSocketSession freshSession;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        ReflectionTestUtils.setField(sessionManager, "applicationContext", applicationContext);
        ReflectionTestUtils.setField(sessionManager, "deviceRegistry", deviceRegistry);

        lenient().when(staleSpringSession.getId()).thenReturn("stale-session");
        lenient().when(freshSpringSession.getId()).thenReturn("fresh-session");
        staleSession = new WebSocketSession(staleSpringSession);
        freshSession = new WebSocketSession(freshSpringSession);
    }

    @Test
    void closingStaleSessionKeepsReconnectedDeviceIndex() {
        register(staleSession);
        // 设备重连：新会话先把映射改指到自己
        register(freshSession);

        // 旧连接的关闭回调此时才到
        sessionManager.removeSession(staleSession.getSessionId());

        assertThat(sessionManager.getSessionByDeviceId(DEVICE_ID)).isSameAs(freshSession);
    }

    @Test
    void closingOwnSessionClearsDeviceIndex() {
        register(staleSession);

        sessionManager.removeSession(staleSession.getSessionId());

        assertThat(sessionManager.getSessionByDeviceId(DEVICE_ID)).isNull();
        assertThat(deviceIndex()).doesNotContainKey(DEVICE_ID);
    }

    @Test
    void staleIndexIsSelfHealedOnLookup() {
        register(staleSession);
        // 会话被非 WebSocket 路径丢弃、只留下反向索引的残留状态
        sessions().remove(staleSession.getSessionId());

        assertThat(sessionManager.getSessionByDeviceId(DEVICE_ID)).isNull();
        assertThat(deviceIndex()).doesNotContainKey(DEVICE_ID);
    }

    @Test
    void deviceWithoutIdIsNotIndexed() {
        sessionManager.registerSession(staleSession.getSessionId(), staleSession);

        sessionManager.registerDevice(staleSession.getSessionId(), new DeviceBO());

        assertThat(deviceIndex()).isEmpty();
    }

    private void register(WebSocketSession session) {
        sessionManager.registerSession(session.getSessionId(), session);
        sessionManager.registerDevice(session.getSessionId(), device());
    }

    private Map<String, String> deviceIndex() {
        @SuppressWarnings("unchecked")
        Map<String, String> index = (Map<String, String>) ReflectionTestUtils.getField(sessionManager, "deviceIdToSessionId");
        return index;
    }

    private Map<String, ?> sessions() {
        @SuppressWarnings("unchecked")
        Map<String, ?> map = (Map<String, ?>) ReflectionTestUtils.getField(sessionManager, "sessions");
        return map;
    }

    private static DeviceBO device() {
        DeviceBO device = new DeviceBO();
        device.setDeviceId(DEVICE_ID);
        return device;
    }
}

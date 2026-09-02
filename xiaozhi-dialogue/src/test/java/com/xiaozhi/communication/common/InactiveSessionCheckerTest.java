package com.xiaozhi.communication.common;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.dialogue.runtime.Persona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 空闲断连按每个会话自己的角色超时算：超时的先说再见、播完才关，
 * 超时配 0 表示永不断连。设备注册表的刷新与断连判定互不牵连。
 */
@ExtendWith(MockitoExtension.class)
class InactiveSessionCheckerTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private DeviceRegistry deviceRegistry;

    private InactiveSessionChecker checker;

    @BeforeEach
    void setUp() {
        checker = new InactiveSessionChecker();
        ReflectionTestUtils.setField(checker, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(checker, "deviceRegistry", deviceRegistry);
    }

    @Test
    void appliesEachSessionsRoleTimeout() {
        ChatSession expired = inactiveSession("expired", 10, 20);
        ChatSession active = inactiveSession("active", 60, 20);
        when(expired.tryBeginInactiveClose()).thenReturn(true);
        when(sessionManager.getAllSessions()).thenReturn(List.of(expired, active));

        checker.checkInactiveSessions();

        verify(sessionManager).closeSession(expired);
        verify(sessionManager, never()).closeSession(active);
    }

    @Test
    void zeroDisablesInactiveTimeout() {
        ChatSession session = inactiveSession("disabled", 0, 3600);
        when(sessionManager.getAllSessions()).thenReturn(List.of(session));

        checker.checkInactiveSessions();

        verify(session, never()).tryBeginInactiveClose();
        verify(sessionManager, never()).closeSession(session);
    }

    @Test
    void sendsGoodbyeOnlyOnceAndWaitsForPlaybackToClose() {
        ChatSession session = inactiveSession("goodbye", 10, 20);
        Persona persona = mock(Persona.class);
        when(session.getPersona()).thenReturn(persona);
        when(session.tryBeginInactiveClose()).thenReturn(true, false);
        when(sessionManager.getAllSessions()).thenReturn(List.of(session));

        checker.checkInactiveSessions();
        checker.checkInactiveSessions();

        verify(persona).sendGoodbyeMessage();
        verify(sessionManager, never()).closeSession(session);
    }

    @Test
    void refreshesDeviceRegistrySeparately() {
        ChatSession session = mock(ChatSession.class);
        DeviceBO device = new DeviceBO();
        device.setDeviceId("device-1");
        when(session.getDevice()).thenReturn(device);
        when(sessionManager.getAllSessions()).thenReturn(List.of(session));

        checker.refreshDeviceRegistry();

        verify(deviceRegistry).refresh("device-1");
    }

    private ChatSession inactiveSession(String sessionId, int timeoutSeconds, long inactiveSeconds) {
        ChatSession session = mock(ChatSession.class);
        lenient().when(session.getSessionId()).thenReturn(sessionId);
        lenient().when(session.isAudioChannelOpen()).thenReturn(true);
        when(session.getInactiveTimeoutSeconds()).thenReturn(timeoutSeconds);
        lenient().when(session.getLastActivityTime()).thenReturn(Instant.now().minusSeconds(inactiveSeconds));
        return session;
    }
}

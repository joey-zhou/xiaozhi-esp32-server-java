package com.xiaozhi.communication.common;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.role.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisSubscriberRoleUpdateTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private RoleService roleService;

    @Mock
    private ChatSession session;

    private RedisSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new RedisSubscriber();
        ReflectionTestUtils.setField(subscriber, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(subscriber, "roleService", roleService);
    }

    @Test
    void refreshesInactiveTimeoutForActiveSessions() {
        DeviceBO device = new DeviceBO();
        device.setRoleId(7);
        RoleBO role = new RoleBO();
        role.setRoleId(7);
        role.setInactiveTimeoutSeconds(25);
        when(roleService.getBO(7)).thenReturn(role);
        when(sessionManager.getAllSessions()).thenReturn(List.of(session));
        when(session.getDevice()).thenReturn(device);
        when(session.getPersona()).thenReturn(null);

        subscriber.onRoleUpdated("7");

        verify(session).setInactiveTimeoutSeconds(25);
    }
}

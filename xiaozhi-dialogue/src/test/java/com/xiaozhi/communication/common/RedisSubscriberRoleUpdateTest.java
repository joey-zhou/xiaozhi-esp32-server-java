package com.xiaozhi.communication.common;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.role.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色改了属性（音色、超时等）要靠这条广播落到本实例的在线会话上：
 * 只清用了这个角色的会话，Persona 不清历史不重建，设备就一直用改前的旧角色说话。
 */
@ExtendWith(MockitoExtension.class)
class RedisSubscriberRoleUpdateTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private RoleService roleService;

    private RedisSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new RedisSubscriber();
        ReflectionTestUtils.setField(subscriber, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(subscriber, "roleService", roleService);
    }

    @Test
    void refreshesInactiveTimeoutForActiveSessions() {
        ChatSession session = sessionWithRole(7);
        when(roleService.getBO(7)).thenReturn(role(25));
        when(sessionManager.getAllSessions()).thenReturn(List.of(session));

        subscriber.onRoleUpdated("7");

        verify(session).setInactiveTimeoutSeconds(25);
    }

    @Test
    void clearsPersonaOfSessionsUsingTheRole() {
        ChatSession session = sessionWithRole(7);
        Persona persona = mock(Persona.class);
        Conversation conversation = mock(Conversation.class);
        when(persona.getConversation()).thenReturn(conversation);
        when(session.getPersona()).thenReturn(persona);
        when(roleService.getBO(7)).thenReturn(role(25));
        when(sessionManager.getAllSessions()).thenReturn(List.of(session));

        subscriber.onRoleUpdated("7");

        // 清历史 + 置空，下一轮对话才会用新角色重建 Persona
        verify(conversation).clear();
        verify(session).setPersona(null);
    }

    @Test
    void leavesSessionsOfOtherRolesUntouched() {
        ChatSession session = sessionWithRole(8);
        when(roleService.getBO(7)).thenReturn(role(25));
        when(sessionManager.getAllSessions()).thenReturn(List.of(session));

        subscriber.onRoleUpdated("7");

        verify(session, never()).setInactiveTimeoutSeconds(anyInt());
        verify(session, never()).setPersona(any());
    }

    private static ChatSession sessionWithRole(int roleId) {
        ChatSession session = mock(ChatSession.class);
        DeviceBO device = new DeviceBO();
        device.setRoleId(roleId);
        when(session.getDevice()).thenReturn(device);
        return session;
    }

    private static RoleBO role(int inactiveTimeoutSeconds) {
        RoleBO role = new RoleBO();
        role.setRoleId(7);
        role.setInactiveTimeoutSeconds(inactiveTimeoutSeconds);
        return role;
    }
}

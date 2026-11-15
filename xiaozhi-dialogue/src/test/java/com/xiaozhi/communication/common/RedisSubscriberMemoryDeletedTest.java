package com.xiaozhi.communication.common;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.MemoryScopeBO;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.utils.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 摘要删了：在线会话开会话时装进提示词的是快照，要丢掉重建。
 * 丢的方式是 discard 而不是 flush——flush 会把手里带着已删内容的上下文再压成摘要写回去。
 */
class RedisSubscriberMemoryDeletedTest {

    private final SessionManager sessionManager = mock(SessionManager.class);
    private RedisSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new RedisSubscriber();
        ReflectionTestUtils.setField(subscriber, "sessionManager", sessionManager);
    }

    @Test
    void liveConversationOfThatRoleIsDiscardedAndRebuiltNextTurn() {
        ChatSession session = sessionWithRole(7);
        Conversation conversation = mock(Conversation.class);
        Persona persona = mock(Persona.class);
        when(persona.getConversation()).thenReturn(conversation);
        when(session.getPersona()).thenReturn(persona);
        when(sessionManager.getSessionByDeviceId("dev-1")).thenReturn(session);

        subscriber.onMemoryDeleted(JsonUtil.toJson(new MemoryScopeBO("dev-1", 7)));

        verify(conversation).discard();
        verify(conversation, never()).flush();
        verify(session).setPersona(null);
    }

    @Test
    void sessionOnAnotherRoleIsLeftAlone() {
        ChatSession session = sessionWithRole(8);
        Conversation conversation = mock(Conversation.class);
        Persona persona = mock(Persona.class);
        when(persona.getConversation()).thenReturn(conversation);
        when(session.getPersona()).thenReturn(persona);
        when(sessionManager.getSessionByDeviceId("dev-1")).thenReturn(session);

        subscriber.onMemoryDeleted(JsonUtil.toJson(new MemoryScopeBO("dev-1", 7)));

        verify(conversation, never()).discard();
        verify(session, never()).setPersona(any());
    }

    private static ChatSession sessionWithRole(int roleId) {
        ChatSession session = mock(ChatSession.class);
        DeviceBO device = new DeviceBO();
        device.setRoleId(roleId);
        when(session.getDevice()).thenReturn(device);
        return session;
    }
}

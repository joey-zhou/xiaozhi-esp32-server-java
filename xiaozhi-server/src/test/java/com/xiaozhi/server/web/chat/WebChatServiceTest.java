package com.xiaozhi.server.web.chat;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.llm.memory.ChatMemory;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.role.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 钉住 Web 聊天会话的空闲回收：会话状态只挂在进程内的 map 上，
 * 浏览器断网、崩溃、直接关标签页都收不到 /chat/close，没有回收就是纯泄漏。
 */
@ExtendWith(MockitoExtension.class)
class WebChatServiceTest {

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private RoleService roleService;

    @Mock
    private ChatMemory chatMemory;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private WebChatService webChatService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webChatService, "maxMessages", 16);
        ReflectionTestUtils.setField(webChatService, "sessionIdleTimeoutMinutes", 30L);
    }

    private String openSession() {
        RoleBO role = new RoleBO();
        role.setRoleId(1);
        role.setRoleDesc("测试角色");
        when(roleService.getBO(1)).thenReturn(role);
        when(chatMemory.find(anyString(), anyInt())).thenReturn(List.of());
        return webChatService.openSession(9, 1);
    }

    @Test
    void idleSessionsAreEvictedBySweep() {
        String sessionId = openSession();
        assertThat(webChatService.hasSession(sessionId)).isTrue();

        // 阈值设为 0 分钟：所有会话的最后活跃时刻都已不晚于回收基准线
        ReflectionTestUtils.setField(webChatService, "sessionIdleTimeoutMinutes", 0L);
        webChatService.evictIdleSessions();

        assertThat(webChatService.hasSession(sessionId)).isFalse();
    }

    @Test
    void freshSessionSurvivesSweep() {
        String sessionId = openSession();

        webChatService.evictIdleSessions();

        assertThat(webChatService.hasSession(sessionId)).isTrue();
    }

    /** 会话被回收后再发消息必须显式报「已过期」，前端据此重新 open。 */
    @Test
    void chatStreamOnClosedSessionFailsFast() {
        String sessionId = openSession();

        webChatService.closeSession(sessionId, 9);

        assertThat(webChatService.hasSession(sessionId)).isFalse();
        assertThatThrownBy(() -> webChatService.chatStream(sessionId, "在吗", 9).blockFirst())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("会话不存在或已过期");
    }

    /** 拿到别人的 sessionId 也不能读/写别人的会话或把它关掉。 */
    @Test
    void chatStreamRejectsMismatchedUser() {
        String sessionId = openSession();

        assertThatThrownBy(() -> webChatService.chatStream(sessionId, "在吗", 999).blockFirst())
            .isInstanceOf(UnauthorizedException.class);
        assertThat(webChatService.hasSession(sessionId)).isTrue();
    }

    @Test
    void closeSessionRejectsMismatchedUser() {
        String sessionId = openSession();

        assertThatThrownBy(() -> webChatService.closeSession(sessionId, 999))
            .isInstanceOf(UnauthorizedException.class);
        assertThat(webChatService.hasSession(sessionId)).isTrue();
    }
}

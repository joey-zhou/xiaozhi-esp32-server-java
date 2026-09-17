package com.xiaozhi.server.web.chat;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.service.TextChatService;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.role.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住 Web 聊天会话编排：空闲回收、会话归属、每轮重新取角色，以及一轮完成后的落库内容。
 * 会话状态只挂在进程内的 map 上，浏览器断网、崩溃、直接关标签页都收不到 /chat/close，没有回收就是纯泄漏。
 */
@ExtendWith(MockitoExtension.class)
class WebChatAppServiceTest {

    @Mock
    private RoleService roleService;

    @Mock
    private MessageService messageService;

    @Mock
    private TextChatService textChatService;

    @InjectMocks
    private WebChatAppService webChatAppService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webChatAppService, "sessionIdleTimeoutMinutes", 30L);
    }

    private RoleBO role() {
        RoleBO role = new RoleBO();
        role.setRoleId(1);
        role.setRoleDesc("测试角色");
        return role;
    }

    private String openSession() {
        when(roleService.getBO(1)).thenReturn(role());
        when(textChatService.openConversation(eq("web:9"), eq(9), any(RoleBO.class), anyString()))
            .thenReturn(mock(Conversation.class));
        return webChatAppService.openSession(9, 1);
    }

    @Test
    void idleSessionsAreEvictedBySweep() {
        String sessionId = openSession();
        assertThat(webChatAppService.hasSession(sessionId)).isTrue();

        // 阈值设为 0 分钟：所有会话的最后活跃时刻都已不晚于回收基准线
        ReflectionTestUtils.setField(webChatAppService, "sessionIdleTimeoutMinutes", 0L);
        webChatAppService.evictIdleSessions();

        assertThat(webChatAppService.hasSession(sessionId)).isFalse();
    }

    @Test
    void freshSessionSurvivesSweep() {
        String sessionId = openSession();

        webChatAppService.evictIdleSessions();

        assertThat(webChatAppService.hasSession(sessionId)).isTrue();
    }

    /** 会话被回收后再发消息必须显式报「已过期」，前端据此重新 open。 */
    @Test
    void chatStreamOnClosedSessionFailsFast() {
        String sessionId = openSession();

        webChatAppService.closeSession(sessionId, 9);

        assertThat(webChatAppService.hasSession(sessionId)).isFalse();
        assertThatThrownBy(() -> webChatAppService.chatStream(sessionId, "在吗", 9).blockFirst())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("会话不存在或已过期");
    }

    /** 拿到别人的 sessionId 也不能读/写别人的会话或把它关掉。 */
    @Test
    void chatStreamRejectsMismatchedUser() {
        String sessionId = openSession();

        assertThatThrownBy(() -> webChatAppService.chatStream(sessionId, "在吗", 999).blockFirst())
            .isInstanceOf(UnauthorizedException.class);
        assertThat(webChatAppService.hasSession(sessionId)).isTrue();
    }

    @Test
    void closeSessionRejectsMismatchedUser() {
        String sessionId = openSession();

        assertThatThrownBy(() -> webChatAppService.closeSession(sessionId, 999))
            .isInstanceOf(UnauthorizedException.class);
        assertThat(webChatAppService.hasSession(sessionId)).isTrue();
    }

    /** 一轮完成后按会话记录的 userId/roleId/sessionId 落库 user、assistant 两条 web 消息。 */
    @Test
    @SuppressWarnings("unchecked")
    void completedTurnPersistsUserAndAssistantMessages() {
        String sessionId = openSession();
        ArgumentCaptor<Consumer<String>> callback = ArgumentCaptor.forClass(Consumer.class);
        when(textChatService.streamTurn(any(Conversation.class), any(RoleBO.class), eq("在吗"),
                any(LocalDateTime.class), callback.capture()))
            .thenReturn(Flux.just(ChatToken.content("在的")));

        webChatAppService.chatStream(sessionId, "在吗", 9).collectList().block();
        callback.getValue().accept("在的");

        ArgumentCaptor<List<MessageBO>> saved = ArgumentCaptor.forClass(List.class);
        verify(messageService, timeout(2000)).saveAll(saved.capture());
        List<MessageBO> messages = saved.getValue();
        assertThat(messages).extracting(MessageBO::getSender)
            .containsExactly(MessageBO.SENDER_USER, MessageBO.SENDER_ASSISTANT);
        assertThat(messages).extracting(MessageBO::getMessage).containsExactly("在吗", "在的");
        assertThat(messages).allSatisfy(message -> {
            assertThat(message.getSource()).isEqualTo(MessageBO.SOURCE_WEB);
            assertThat(message.getDeviceId()).isEqualTo("web:9");
            assertThat(message.getUserId()).isEqualTo(9);
            assertThat(message.getRoleId()).isEqualTo(1);
            assertThat(message.getSessionId()).isEqualTo(sessionId);
        });
    }

    /** 会话开着期间角色被删，下一轮必须报错，不能拿旧角色继续聊。 */
    @Test
    void chatStreamFailsWhenRoleDeleted() {
        String sessionId = openSession();
        when(roleService.getBO(1)).thenReturn(null);

        assertThatThrownBy(() -> webChatAppService.chatStream(sessionId, "在吗", 9).blockFirst())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("角色不存在");
    }

    /** 每轮都重新取角色，角色或模型配置改了下一轮就生效。 */
    @Test
    void eachTurnReloadsRole() {
        String sessionId = openSession();
        when(textChatService.streamTurn(any(Conversation.class), any(RoleBO.class), anyString(),
                any(LocalDateTime.class), any()))
            .thenReturn(Flux.empty());

        webChatAppService.chatStream(sessionId, "第一轮", 9).collectList().block();
        webChatAppService.chatStream(sessionId, "第二轮", 9).collectList().block();

        // openSession 一次 + 两轮各一次
        verify(roleService, times(3)).getBO(1);
        verify(textChatService, times(2)).streamTurn(any(Conversation.class), any(RoleBO.class), anyString(),
            any(LocalDateTime.class), any());
    }
}

package com.xiaozhi.server.web.chat;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.service.TextChatService;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.ConversationBO;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.message.service.ConversationService;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.server.web.chat.convert.WebChatConvert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住 Web 聊天会话编排：空闲回收、会话归属、每轮重新取角色、会话表的建档与删除，以及一轮完成后的落库内容。
 * 会话状态只挂在进程内的 map 上，浏览器断网、崩溃、直接关标签页都收不到 /chat/close，没有回收就是纯泄漏。
 */
@ExtendWith(MockitoExtension.class)
class WebChatAppServiceTest {

    @Mock
    private RoleService roleService;

    @Mock
    private MessageService messageService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private TextChatService textChatService;

    @InjectMocks
    private WebChatAppService webChatAppService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webChatAppService, "sessionIdleTimeoutMinutes", 30L);
        ReflectionTestUtils.setField(webChatAppService, "webChatConvert", Mappers.getMapper(WebChatConvert.class));
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

    /** streamTurn 的替身：像真实现一样在流结束前回调一轮完成 */
    private static Answer<Flux<ChatToken>> completedTurn(String reply, LocalDateTime assistantCreatedAt) {
        return invocation -> {
            BiConsumer<String, LocalDateTime> callback = invocation.getArgument(4);
            return Flux.just(ChatToken.content(reply)).doOnComplete(() -> callback.accept(reply, assistantCreatedAt));
        };
    }

    private static ConversationBO savedConversation(String sessionId, Integer userId) {
        ConversationBO conversation = new ConversationBO();
        conversation.setSessionId(sessionId);
        conversation.setUserId(userId);
        conversation.setRoleId(1);
        return conversation;
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

    /** 关闭会话时把剩下的对话压成摘要，否则没触发过压缩的短会话永远进不了摘要。 */
    @Test
    void closeSessionFlushesTheConversation() {
        Conversation conversation = mock(Conversation.class);
        when(roleService.getBO(1)).thenReturn(role());
        when(textChatService.openConversation(eq("web:9"), eq(9), any(RoleBO.class), anyString()))
            .thenReturn(conversation);
        String sessionId = webChatAppService.openSession(9, 1);

        webChatAppService.closeSession(sessionId, 9);

        verify(conversation).flush();
    }

    /** 进程内会话只是缓存：被回收或重启后拿同一个 sessionId 再聊，按会话表重建，不要求前端重新 open。 */
    @Test
    void chatStreamRebuildsEvictedSessionFromConversationTable() {
        String sessionId = openSession();
        webChatAppService.closeSession(sessionId, 9);
        assertThat(webChatAppService.hasSession(sessionId)).isFalse();
        when(conversationService.get(sessionId)).thenReturn(savedConversation(sessionId, 9));
        when(textChatService.streamTurn(any(Conversation.class), any(RoleBO.class), anyString(),
                any(LocalDateTime.class), any()))
            .thenReturn(Flux.empty());

        webChatAppService.chatStream(sessionId, "在吗", 9).collectList().block();

        assertThat(webChatAppService.hasSession(sessionId)).isTrue();
        verify(textChatService, times(2)).openConversation(eq("web:9"), eq(9), any(RoleBO.class), eq(sessionId));
        verify(conversationService, never()).create(anyString(), any(), any(), anyString());
    }

    /** 会话表里也没有的 sessionId 才是真的不存在。 */
    @Test
    void chatStreamOnUnknownSessionFails() {
        when(conversationService.get("ghost")).thenReturn(null);

        assertThatThrownBy(() -> webChatAppService.chatStream("ghost", "在吗", 9).blockFirst())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("会话不存在或已删除");
        assertThat(webChatAppService.hasSession("ghost")).isFalse();
    }

    /** 重建路径同样按会话表校验归属，拿别人的 sessionId 不能把别人的会话建到自己名下。 */
    @Test
    void chatStreamDoesNotRebuildAnotherUsersSession() {
        when(conversationService.get("s-8")).thenReturn(savedConversation("s-8", 8));

        assertThatThrownBy(() -> webChatAppService.chatStream("s-8", "在吗", 9).blockFirst())
            .isInstanceOf(UnauthorizedException.class);
        assertThat(webChatAppService.hasSession("s-8")).isFalse();
        verify(textChatService, never()).openConversation(anyString(), any(), any(), anyString());
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

    /** 一轮完成后按会话记录的 userId/roleId/sessionId 落库 user、assistant 两条 web 消息，并刷新会话的最后对话时间。 */
    @Test
    @SuppressWarnings("unchecked")
    void completedTurnPersistsUserAndAssistantMessages() {
        String sessionId = openSession();
        LocalDateTime assistantCreatedAt = LocalDateTime.of(2026, 9, 15, 7, 33, 2, 424_000_000);
        when(textChatService.streamTurn(any(Conversation.class), any(RoleBO.class), eq("在吗"),
                any(LocalDateTime.class), any()))
            .thenAnswer(completedTurn("在的", assistantCreatedAt));

        webChatAppService.chatStream(sessionId, "在吗", 9).collectList().block();

        ArgumentCaptor<List<MessageBO>> saved = ArgumentCaptor.forClass(List.class);
        verify(messageService, timeout(2000)).saveAll(saved.capture());
        verify(conversationService, timeout(2000)).touch(sessionId);
        verify(conversationService, never()).delete(any(), any());
        List<MessageBO> messages = saved.getValue();
        assertThat(messages).extracting(MessageBO::getSender)
            .containsExactly(MessageBO.SENDER_USER, MessageBO.SENDER_ASSISTANT);
        assertThat(messages).extracting(MessageBO::getMessage).containsExactly("在吗", "在的");
        assertThat(messages.get(1).getCreateTime()).isEqualTo(assistantCreatedAt);
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

    /** 每轮都重新取角色，角色或模型配置改了下一轮就生效；新会话只在第一轮建档，标题取第一句话。 */
    @Test
    void eachTurnReloadsRole() {
        String sessionId = openSession();
        when(textChatService.streamTurn(any(Conversation.class), any(RoleBO.class), anyString(),
                any(LocalDateTime.class), any()))
            .thenAnswer(completedTurn("好的", LocalDateTime.now()));

        webChatAppService.chatStream(sessionId, "第一轮", 9).collectList().block();
        webChatAppService.chatStream(sessionId, "第二轮", 9).collectList().block();

        // openSession 一次 + 两轮各一次
        verify(roleService, times(3)).getBO(1);
        verify(textChatService, times(2)).streamTurn(any(Conversation.class), any(RoleBO.class), anyString(),
            any(LocalDateTime.class), any());
        verify(conversationService, timeout(2000)).create(sessionId, 9, 1, "第一轮");
        verify(messageService, timeout(2000).times(2)).saveAll(any());
        verify(conversationService, times(1)).create(anyString(), any(), any(), anyString());
    }

    /** 首轮没聊成（模型报错）就撤掉刚建的档，否则侧栏留下一个打开是空的会话；下一轮重新建档。 */
    @Test
    void failedFirstTurnDiscardsTheEmptyConversationAndCreatesItAgainNextTime() {
        String sessionId = openSession();
        when(textChatService.streamTurn(any(Conversation.class), any(RoleBO.class), anyString(),
                any(LocalDateTime.class), any()))
            .thenReturn(Flux.just(ChatToken.error("模型不可用")))
            .thenAnswer(completedTurn("在的", LocalDateTime.now()));

        webChatAppService.chatStream(sessionId, "你好", 9).collectList().block();
        verify(conversationService, timeout(2000)).create(sessionId, 9, 1, "你好");
        verify(conversationService, timeout(2000)).delete(9, List.of(sessionId));

        webChatAppService.chatStream(sessionId, "再试一次", 9).collectList().block();
        verify(conversationService, timeout(2000)).create(sessionId, 9, 1, "再试一次");
        verify(messageService, timeout(2000)).saveAll(any());
        verify(conversationService, times(1)).delete(any(), any());
    }

    /** 同一个会话在另一个标签页已经开着时直接复用，再建一份会让两个 Conversation 各压缩一遍同一段历史。 */
    @Test
    void resumingASessionAlreadyOpenIsReused() {
        String sessionId = openSession();

        assertThat(webChatAppService.openSession(9, 1, sessionId)).isEqualTo(sessionId);

        verify(textChatService, times(1)).openConversation(eq("web:9"), eq(9), any(RoleBO.class), eq(sessionId));
        verify(conversationService, never()).get(anyString());
    }

    /** 续接按会话表校验归属，续接的会话已经建过档，不再重复创建。 */
    @Test
    void resumedSessionIsNotCreatedAgain() {
        when(conversationService.get("s-1")).thenReturn(savedConversation("s-1", 9));
        when(roleService.getBO(1)).thenReturn(role());
        when(textChatService.openConversation(eq("web:9"), eq(9), any(RoleBO.class), eq("s-1")))
            .thenReturn(mock(Conversation.class));
        when(textChatService.streamTurn(any(Conversation.class), any(RoleBO.class), anyString(),
                any(LocalDateTime.class), any()))
            .thenReturn(Flux.empty());

        webChatAppService.openSession(9, 1, "s-1");
        webChatAppService.chatStream("s-1", "接着聊", 9).collectList().block();

        verify(conversationService, never()).create(anyString(), any(), any(), anyString());
    }

    @Test
    void resumingAnotherUsersConversationIsRejected() {
        when(roleService.getBO(1)).thenReturn(role());
        when(conversationService.get("s-1")).thenReturn(savedConversation("s-1", 8));

        assertThatThrownBy(() -> webChatAppService.openSession(9, 1, "s-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不属于当前用户");
    }

    /** 删除会话时进程里开着的会话一并移除，不再压缩出挂在已删除会话上的摘要。 */
    @Test
    void deletingConversationDropsTheOpenSessionWithoutFlushing() {
        Conversation conversation = mock(Conversation.class);
        when(roleService.getBO(1)).thenReturn(role());
        when(textChatService.openConversation(eq("web:9"), eq(9), any(RoleBO.class), anyString()))
            .thenReturn(conversation);
        String sessionId = webChatAppService.openSession(9, 1);
        when(conversationService.delete(9, List.of(sessionId))).thenReturn(List.of(sessionId));

        assertThat(webChatAppService.deleteConversations(9, List.of(sessionId))).isEqualTo(1);

        assertThat(webChatAppService.hasSession(sessionId)).isFalse();
        verify(conversation, never()).flush();
        verify(conversation).discard();
    }

    @Test
    void deletingDoesNotDropAnotherUsersOpenSession() {
        String sessionId = openSession();
        when(conversationService.delete(999, List.of(sessionId))).thenReturn(List.of());

        assertThat(webChatAppService.deleteConversations(999, List.of(sessionId))).isZero();

        assertThat(webChatAppService.hasSession(sessionId)).isTrue();
    }
}

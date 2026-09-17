package com.xiaozhi.server.web.chat;

import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.ConversationBO;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.message.convert.MessageConvert;
import com.xiaozhi.message.service.ConversationService;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 钉住会话接口按当前登录用户取数、角色名由 Service 带出原样下发；删除必须走编排服务，它要顺带移除进程里还开着的会话。
 */
@ExtendWith(MockitoExtension.class)
class ConversationControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private ConversationService conversationService;

    @Mock
    private WebChatAppService webChatAppService;

    @BeforeEach
    void setUp() {
        ConversationController controller = new ConversationController();
        ReflectionTestUtils.setField(controller, "conversationService", conversationService);
        ReflectionTestUtils.setField(controller, "webChatAppService", webChatAppService);
        ReflectionTestUtils.setField(controller, "messageConvert", Mappers.getMapper(MessageConvert.class));
        mockMvc = buildMockMvc(controller);
    }

    @Test
    void listReturnsCurrentUsersConversationsWithRoleName() throws Exception {
        ConversationBO conversation = new ConversationBO();
        conversation.setSessionId("s-1");
        conversation.setUserId(7);
        conversation.setRoleId(3);
        conversation.setRoleName("小智");
        conversation.setTitle("今天天气怎么样");
        conversation.setUpdateTime(LocalDateTime.of(2026, 9, 14, 10, 0));
        when(conversationService.page(7, null, 1, 10)).thenReturn(new PageResult<>(List.of(conversation), 1L, 1, 10));

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(get("/api/conversations").param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.data.list[0].sessionId").value("s-1"))
                .andExpect(jsonPath("$.data.list[0].title").value("今天天气怎么样"))
                .andExpect(jsonPath("$.data.list[0].roleName").value("小智"))
                .andExpect(jsonPath("$.data.list[0].updateTime").value("2026-09-14 10:00:00"));
        }
    }

    @Test
    void renameUsesTheCurrentUser() throws Exception {
        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(patch("/api/conversations/s-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"title":"周末计划"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS));
        }

        verify(conversationService).rename(7, "s-1", "周末计划");
    }

    @Test
    void renameRejectsBlankTitle() throws Exception {
        mockMvc.perform(patch("/api/conversations/s-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"  "}
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(conversationService);
    }

    @Test
    void deleteGoesThroughTheChatAppService() throws Exception {
        when(webChatAppService.deleteConversations(7, List.of("s-1", "s-2"))).thenReturn(2);

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(delete("/api/conversations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"sessionIds":["s-1","s-2"]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
        }
    }

    @Test
    void deleteRejectsEmptySelection() throws Exception {
        mockMvc.perform(delete("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionIds":[]}
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(webChatAppService);
    }
}

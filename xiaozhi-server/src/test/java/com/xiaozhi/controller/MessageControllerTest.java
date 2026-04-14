package com.xiaozhi.controller;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.message.MessageController;
import com.xiaozhi.common.model.req.MessagePageReq;
import com.xiaozhi.message.MessageAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private MessageAppService messageAppService;

    private MessageController messageController;

    @BeforeEach
    void setUp() {
        messageController = new MessageController();
        ReflectionTestUtils.setField(messageController, "messageAppService", messageAppService);
        mockMvc = buildMockMvc(messageController);
    }

    @Test
    void listReturnsPagedMessagesForCurrentUser() throws Exception {
        MessageResp messageResp = new MessageResp();
        messageResp.setMessageId(1);
        messageResp.setDeviceId("dev-1");
        PageResp<MessageResp> pageResp = new PageResp<>(List.of(messageResp), 1L, 1, 10);
        when(messageAppService.page(any(MessagePageReq.class), eq(7))).thenReturn(pageResp);

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(get("/api/message")
                    .param("pageNo", "1")
                    .param("pageSize", "10")
                    .param("deviceId", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.data.list[0].messageId").value(1));
        }

        verify(messageAppService).page(any(MessagePageReq.class), eq(7));
    }

    @Test
    void deleteReturnsNotFoundWhenMessageMissing() throws Exception {
        doThrow(new ResourceNotFoundException("消息不存在或无权访问")).when(messageAppService).delete(5);

        mockMvc.perform(delete("/api/message/5"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ResultStatus.NOT_FOUND))
            .andExpect(jsonPath("$.message").value("消息不存在或无权访问"));
    }

    @Test
    void batchDeleteReturnsDeletedCountMessage() throws Exception {
        when(messageAppService.deleteByDeviceId("dev-1")).thenReturn(3);

        mockMvc.perform(delete("/api/message").param("deviceId", "dev-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
            .andExpect(jsonPath("$.message").value("删除成功，共删除3条消息"));
    }
}

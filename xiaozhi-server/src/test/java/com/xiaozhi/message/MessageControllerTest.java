package com.xiaozhi.message;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.message.convert.MessageConvert;
import com.xiaozhi.message.model.MessageProjection;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 钉住消息接口的分页参数绑定、按设备批量删除的条数文案，以及单条删除的路由与路径变量绑定。
 */
@ExtendWith(MockitoExtension.class)
class MessageControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private MessageService messageService;

    private MessageController messageController;

    @BeforeEach
    void setUp() {
        messageController = new MessageController();
        ReflectionTestUtils.setField(messageController, "messageService", messageService);
        ReflectionTestUtils.setField(messageController, "messageConvert", Mappers.getMapper(MessageConvert.class));
        mockMvc = buildMockMvc(messageController);
    }

    @Test
    void listReturnsPagedMessagesForCurrentUser() throws Exception {
        MessageProjection message = new MessageProjection();
        message.setMessageId(1L);
        message.setDeviceId("dev-1");
        when(messageService.page(1, 10, "dev-1", null, null, null, null, null, null, 7, null, null))
            .thenReturn(new PageResult<>(List.of(message), 1L, 1, 10));

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(get("/api/message")
                    .param("pageNo", "1")
                    .param("pageSize", "10")
                    .param("deviceId", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.data.list[0].messageId").value(1));
        }

        verify(messageService).page(1, 10, "dev-1", null, null, null, null, null, null, 7, null, null);
    }

    // 异常文案由 GlobalExceptionHandlerTest 集中覆盖，这里只钉路由与路径变量绑定
    @Test
    void deleteReturnsNotFoundWhenMessageMissing() throws Exception {
        doThrow(new ResourceNotFoundException("消息不存在或无权访问")).when(messageService).delete(5L);

        mockMvc.perform(delete("/api/message/5"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(ResultStatus.NOT_FOUND));
    }

    @Test
    void batchDeleteReturnsDeletedCountMessage() throws Exception {
        when(messageService.deleteByDeviceId("dev-1")).thenReturn(3);

        mockMvc.perform(delete("/api/message").param("deviceId", "dev-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
            .andExpect(jsonPath("$.message").value("删除成功，共删除3条消息"));
    }
}

package com.xiaozhi.server.web.chat;

import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.server.web.chat.convert.WebChatConvert;
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
import reactor.core.publisher.Flux;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 钉住流式聊天的入参位置：用户输入只能走请求体。
 * 一旦退回 GET + query，用户说的每句话都会落进 access log 与反向代理日志。
 */
@ExtendWith(MockitoExtension.class)
class WebChatControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private WebChatService webChatService;

    @BeforeEach
    void setUp() {
        WebChatController controller = new WebChatController();
        ReflectionTestUtils.setField(controller, "webChatService", webChatService);
        ReflectionTestUtils.setField(controller, "webChatConvert", Mappers.getMapper(WebChatConvert.class));
        mockMvc = buildMockMvc(controller);
    }

    @Test
    void streamTakesSessionIdAndTextFromBody() throws Exception {
        when(webChatService.chatStream("s-1", "你好")).thenReturn(Flux.just(ChatToken.content("在的")));

        mockMvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId":"s-1","text":"你好"}
                    """));

        verify(webChatService).chatStream("s-1", "你好");
    }

    @Test
    void streamNoLongerAcceptsGet() throws Exception {
        mockMvc.perform(get("/api/chat/stream").param("sessionId", "s-1").param("text", "你好"))
            .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(webChatService);
    }

    @Test
    void streamRejectsBlankText() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId":"s-1","text":"  "}
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(webChatService);
    }
}

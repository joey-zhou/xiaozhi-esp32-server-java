package com.xiaozhi.ai.llm.service;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.llm.memory.ChatMemory;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.RoleBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住文本聊天一轮的收尾：只有正常完成且正文非空才算一轮并回调落库，
 * 报错、取消、空回复都要把孤立的用户消息摘掉；失败原因以 error token 推给前端而不是抛出。
 */
@ExtendWith(MockitoExtension.class)
class TextChatServiceTest {

    private static final String SESSION_ID = "session-1";

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ChatMemory chatMemory;

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private TextChatService textChatService;

    private final RoleBO role = new RoleBO();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(textChatService, "maxMessages", 16);
        role.setRoleId(1);
        role.setRoleDesc("测试角色");
    }

    private Conversation openConversation() {
        when(chatMemory.findBySession(SESSION_ID, 16)).thenReturn(List.of());
        return textChatService.openConversation("web:9", 9, role, SESSION_ID);
    }

    private void modelStreams(Flux<ChatResponse> responses) {
        when(chatModelFactory.getChatModel(role)).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenReturn(responses);
    }

    private static ChatResponse content(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse chunk(String reasoning, String text) {
        AssistantMessage message = AssistantMessage.builder()
                .content(text)
                .properties(Map.of("reasoningContent", reasoning))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static final class RecordingCallback implements Consumer<String> {
        private final List<String> replies = new ArrayList<>();

        @Override
        public void accept(String reply) {
            replies.add(reply);
        }
    }

    @Test
    void openConversationLoadsHistoryBySessionId() {
        Conversation conversation = openConversation();

        verify(chatMemory).findBySession(SESSION_ID, 16);
        assertThat(conversation.sessionId()).isEqualTo(SESSION_ID);
        assertThat(conversation.getOwnerId()).isEqualTo("web:9");
        assertThat(conversation.getUserId()).isEqualTo(9);
        assertThat(conversation.getRoleId()).isEqualTo(1);
    }

    /** 回调只触发一次且只含正文，思考过程不落库；对话里留下 User + Assistant 一组。 */
    @Test
    void completedTurnInvokesCallbackOnceWithContentOnly() {
        Conversation conversation = openConversation();
        modelStreams(Flux.just(chunk("先想想", "你"), content("好")));
        RecordingCallback callback = new RecordingCallback();

        List<ChatToken> tokens = textChatService
                .streamTurn(conversation, role, "在吗", LocalDateTime.now(), callback)
                .collectList().block();

        assertThat(tokens).containsExactly(
                ChatToken.thinking("先想想"), ChatToken.content("你"), ChatToken.content("好"));
        assertThat(callback.replies).containsExactly("你好");
        List<Message> messages = conversation.rawMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("在吗");
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("你好");
    }

    /** 服务商返回错误响应时，状态码与响应体作为 error token 推给前端，流正常结束不抛异常。 */
    @Test
    void upstreamErrorResponseBecomesErrorToken() {
        Conversation conversation = openConversation();
        byte[] body = "{\"error\":\"invalid api key\"}".getBytes(StandardCharsets.UTF_8);
        modelStreams(Flux.error(
                WebClientResponseException.create(401, "Unauthorized", HttpHeaders.EMPTY, body, StandardCharsets.UTF_8)));
        RecordingCallback callback = new RecordingCallback();

        List<ChatToken> tokens = textChatService
                .streamTurn(conversation, role, "在吗", LocalDateTime.now(), callback)
                .collectList().block();

        assertThat(tokens).containsExactly(ChatToken.error("HTTP 401 {\"error\":\"invalid api key\"}"));
        assertThat(callback.replies).isEmpty();
        assertThat(conversation.rawMessages()).isEmpty();
    }

    /** 模型吐出半截正文后报错：错误被转成正常完成，但半截正文不能算一轮。 */
    @Test
    void partialContentThenErrorDoesNotCompleteTurn() {
        Conversation conversation = openConversation();
        modelStreams(Flux.just(content("说到一半"))
                .concatWith(Flux.<ChatResponse>error(new IllegalStateException("连接中断"))));
        RecordingCallback callback = new RecordingCallback();

        List<ChatToken> tokens = textChatService
                .streamTurn(conversation, role, "在吗", LocalDateTime.now(), callback)
                .collectList().block();

        assertThat(tokens).containsExactly(ChatToken.content("说到一半"), ChatToken.error("连接中断"));
        assertThat(callback.replies).isEmpty();
        assertThat(conversation.rawMessages()).isEmpty();
    }

    @Test
    void emptyReplyDoesNotCompleteTurn() {
        Conversation conversation = openConversation();
        modelStreams(Flux.just(content("")));
        RecordingCallback callback = new RecordingCallback();

        List<ChatToken> tokens = textChatService
                .streamTurn(conversation, role, "在吗", LocalDateTime.now(), callback)
                .collectList().block();

        assertThat(tokens).isEmpty();
        assertThat(callback.replies).isEmpty();
        assertThat(conversation.rawMessages()).isEmpty();
    }

    /** 客户端中途断开（下游取消）时，本轮用户消息要摘掉。 */
    @Test
    void cancelledTurnRemovesUserMessage() {
        Conversation conversation = openConversation();
        modelStreams(Flux.just(content("第一段"), content("第二段")));
        RecordingCallback callback = new RecordingCallback();

        List<ChatToken> tokens = textChatService
                .streamTurn(conversation, role, "在吗", LocalDateTime.now(), callback)
                .take(1)
                .collectList().block();

        assertThat(tokens).containsExactly(ChatToken.content("第一段"));
        assertThat(callback.replies).isEmpty();
        assertThat(conversation.rawMessages()).isEmpty();
    }

    @Test
    void restClientErrorResponseIsDescribedWithStatusAndBody() {
        byte[] body = "rate limited".getBytes(StandardCharsets.UTF_8);
        HttpClientErrorException error = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, body, StandardCharsets.UTF_8);

        assertThat(TextChatService.describeFailure(new RuntimeException("调用失败", error)))
                .isEqualTo("HTTP 429 rate limited");
    }

    @Test
    void describeFailureFallsBackToDeepestMessage() {
        RuntimeException error = new RuntimeException("流式调用失败", new IllegalStateException("连接超时"));

        assertThat(TextChatService.describeFailure(error)).isEqualTo("连接超时");
    }

    @Test
    void describeFailureTruncatesLongReason() {
        String reason = TextChatService.describeFailure(new IllegalStateException("x".repeat(2000)));

        assertThat(reason).hasSize(501).endsWith("…");
    }

    /** 同一块里思考在前、正文在后；没有 result 或 output 的块跳过。 */
    @Test
    void toChatTokensOrdersReasoningBeforeContentAndSkipsEmptyChunks() {
        Flux<ChatResponse> responses = Flux.just(
                new ChatResponse(List.of()),
                new ChatResponse(List.of(new Generation(null))),
                chunk("思考", "回答"),
                content(""));

        List<ChatToken> tokens = TextChatService.toChatTokens(responses).collectList().block();

        assertThat(tokens).containsExactly(ChatToken.thinking("思考"), ChatToken.content("回答"));
    }
}

package com.xiaozhi.ai.llm.service;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.llm.memory.ChatMemory;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.ConversationFactory;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.RoleBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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
    private ConversationFactory conversationFactory;

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private TextChatService textChatService;

    private final RoleBO role = new RoleBO();

    @BeforeEach
    void setUp() {
        role.setRoleId(1);
        role.setRoleDesc("测试角色");
    }

    private Conversation openConversation() {
        when(conversationFactory.initSessionConversation("web:9", 9, role, SESSION_ID))
                .thenReturn(Conversation.of("web:9", 1, SESSION_ID, "测试角色", 9));
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

    private static final class RecordingCallback implements BiConsumer<String, LocalDateTime> {
        private final List<String> replies = new ArrayList<>();
        private final List<LocalDateTime> assistantCreatedAts = new ArrayList<>();

        @Override
        public void accept(String reply, LocalDateTime assistantCreatedAt) {
            replies.add(reply);
            assistantCreatedAts.add(assistantCreatedAt);
        }
    }

    @Test
    void openConversationIsScopedToTheSession() {
        Conversation conversation = openConversation();

        verify(conversationFactory).initSessionConversation("web:9", 9, role, SESSION_ID);
        assertThat(conversation.sessionId()).isEqualTo(SESSION_ID);
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

    /**
     * 助手消息的时间元数据必须和回调给落库的时间是同一个。
     * 没有时间元数据时摘要会拿「写摘要的时刻」当切点，重载历史时摘要之后到那一刻之间的消息全部丢失。
     */
    @Test
    void assistantMessageCarriesTheSameTimeAsHandedToCallback() {
        Conversation conversation = openConversation();
        modelStreams(Flux.just(content("好")));
        RecordingCallback callback = new RecordingCallback();

        textChatService.streamTurn(conversation, role, "在吗", LocalDateTime.now(), callback)
                .collectList().block();

        Message assistant = conversation.rawMessages().get(1);
        assertThat(callback.assistantCreatedAts).hasSize(1);
        assertThat(MessageTimeMetadata.getTimeMillis(assistant))
                .isEqualTo(callback.assistantCreatedAts.getFirst().atZone(ZoneId.systemDefault()).toInstant());
    }

    /** 流式最后一块带回的用量挂到助手消息上，对话据此判断上下文是否超限。 */
    @Test
    void completedTurnCarriesUsageOnAssistantMessage() {
        Conversation conversation = openConversation();
        ChatResponse last = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("好"))))
                .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(1200, 20)).build())
                .build();
        modelStreams(Flux.just(content("你"), last));

        textChatService.streamTurn(conversation, role, "在吗", LocalDateTime.now(), new RecordingCallback())
                .collectList().block();

        Object usage = conversation.rawMessages().get(1).getMetadata().get(ChatMemory.USAGE_KEY);
        assertThat(usage).isInstanceOf(Usage.class);
        assertThat(((Usage) usage).getPromptTokens()).isEqualTo(1200);
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

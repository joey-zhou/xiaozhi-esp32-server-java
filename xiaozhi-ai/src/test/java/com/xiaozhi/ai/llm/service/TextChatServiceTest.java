package com.xiaozhi.ai.llm.service;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.llm.memory.ChatMemory;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.ConversationFactory;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import com.xiaozhi.ai.llm.memory.ToolChainPair;
import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.ChatImageBO;
import com.xiaozhi.common.model.bo.ConfigBO;
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
import com.xiaozhi.ai.tool.ToolCallActivityRegistry;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    @Mock
    private VisionService visionService;

    @Spy
    private ModelTimeoutPolicy modelTimeoutPolicy =
            new ModelTimeoutPolicy(Duration.ofSeconds(30), Duration.ofSeconds(90), new ToolCallActivityRegistry());

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

    private static final class RecordingCallback implements Consumer<TextChatService.CompletedTurn> {
        private final List<String> replies = new ArrayList<>();
        private final List<LocalDateTime> assistantCreatedAts = new ArrayList<>();
        private final List<List<ToolChainPair>> toolChains = new ArrayList<>();

        @Override
        public void accept(TextChatService.CompletedTurn turn) {
            replies.add(turn.reply());
            assistantCreatedAts.add(turn.assistantCreatedAt());
            toolChains.add(turn.toolChains());
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
                .streamTurn(conversation, role, "在吗", List.of(), LocalDateTime.now(), callback)
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

    /** 角色模型能看图就直接给它，对话里只留文字 */
    @Test
    void imagesGoStraightToAModelThatCanSeeThem() {
        Conversation conversation = openConversation();
        ChatImageBO photo = new ChatImageBO("image/png", new byte[]{1, 2, 3});
        when(chatModelFactory.resolveConfig(role)).thenReturn(new ConfigBO().setImageInput(true));
        when(chatModelFactory.getChatModel(role)).thenReturn(chatModel);
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> {
            List<Message> sent = invocation.<Prompt>getArgument(0).getInstructions();
            UserMessage asked = (UserMessage) sent.getLast();
            assertThat(asked.getText()).endsWith("这是什么花");
            assertThat(asked.getMedia()).singleElement()
                    .satisfies(media -> assertThat(media.getMimeType().toString()).isEqualTo("image/png"));
            return Flux.just(content("是月季"));
        });
        RecordingCallback callback = new RecordingCallback();

        List<ChatToken> tokens = textChatService
                .streamTurn(conversation, role, "这是什么花", List.of(photo), LocalDateTime.now(), callback)
                .collectList().block();

        assertThat(tokens).containsExactly(ChatToken.content("是月季"));
        verify(visionService, never()).describe(any(), any());
        assertThat(callback.toolChains.getFirst()).isEmpty();
        assertThat(conversation.rawMessages()).hasSize(2);
        assertThat(((UserMessage) conversation.rawMessages().getFirst()).getMedia()).isEmpty();
    }

    /** 角色模型看不了图时由视觉模型先看，结果作为调用链放进对话 */
    @Test
    void imagesAreLookedAtByTheVisionModelBeforeAnswering() {
        Conversation conversation = openConversation();
        ChatImageBO photo = new ChatImageBO("image/png", new byte[]{1, 2, 3});
        when(chatModelFactory.resolveConfig(role)).thenReturn(new ConfigBO());
        when(visionService.describe(eq(List.of(photo)), contains("这是什么花"))).thenReturn("一朵红色的月季");
        modelStreams(Flux.just(content("是月季")));
        RecordingCallback callback = new RecordingCallback();

        List<ChatToken> tokens = textChatService
                .streamTurn(conversation, role, "这是什么花", List.of(photo), LocalDateTime.now(), callback)
                .collectList().block();

        assertThat(tokens).containsExactly(ChatToken.activity(ChatToken.ACTIVITY_VISION), ChatToken.content("是月季"));
        ToolChainPair looked = callback.toolChains.getFirst().getFirst();
        assertThat(looked.toolResponseMessage().getResponses().getFirst().responseData()).isEqualTo("一朵红色的月季");
        assertThat(conversation.rawMessages()).hasSize(4);
        assertThat(conversation.rawMessages().get(2)).isSameAs(looked.toolResponseMessage());
    }

    /** 看图失败这一轮就失败：原因推给前端，不调主模型 */
    @Test
    void aFailedLookFailsTheTurn() {
        Conversation conversation = openConversation();
        ChatImageBO photo = new ChatImageBO("image/png", new byte[]{1});
        when(chatModelFactory.resolveConfig(role)).thenReturn(new ConfigBO());
        when(chatModelFactory.getChatModel(role)).thenReturn(chatModel);
        when(visionService.describe(any(), any())).thenThrow(new IllegalStateException("没有可用的视觉模型"));
        RecordingCallback callback = new RecordingCallback();

        List<ChatToken> tokens = textChatService
                .streamTurn(conversation, role, "这是什么花", List.of(photo), LocalDateTime.now(), callback)
                .collectList().block();

        assertThat(tokens).containsExactly(
                ChatToken.activity(ChatToken.ACTIVITY_VISION), ChatToken.error("没有可用的视觉模型"));
        assertThat(callback.replies).isEmpty();
        assertThat(conversation.rawMessages()).isEmpty();
        verify(chatModel, never()).stream(any(Prompt.class));
    }

    /** 模型一直不出字就按超时结束，给用户看的是中文原因，这一轮不留在对话里 */
    @Test
    void aModelThatNeverAnswersTimesOut() {
        ReflectionTestUtils.setField(textChatService, "modelTimeoutPolicy",
                new ModelTimeoutPolicy(Duration.ofMillis(100), Duration.ofMillis(100), new ToolCallActivityRegistry()));
        Conversation conversation = openConversation();
        modelStreams(Flux.never());

        List<ChatToken> tokens = textChatService
                .streamTurn(conversation, role, "在吗", List.of(), LocalDateTime.now(), new RecordingCallback())
                .collectList().block(Duration.ofSeconds(5));

        assertThat(tokens).containsExactly(ChatToken.error("模型长时间没有响应，请稍后再试"));
        assertThat(conversation.rawMessages()).isEmpty();
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

        textChatService.streamTurn(conversation, role, "在吗", List.of(), LocalDateTime.now(), callback)
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

        textChatService.streamTurn(conversation, role, "在吗", List.of(), LocalDateTime.now(), new RecordingCallback())
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
                .streamTurn(conversation, role, "在吗", List.of(), LocalDateTime.now(), callback)
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
                .streamTurn(conversation, role, "在吗", List.of(), LocalDateTime.now(), callback)
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
                .streamTurn(conversation, role, "在吗", List.of(), LocalDateTime.now(), callback)
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
                .streamTurn(conversation, role, "在吗", List.of(), LocalDateTime.now(), callback)
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

package com.xiaozhi.ai.llm.service;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.llm.memory.ChatMemory;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.ConversationContext;
import com.xiaozhi.ai.llm.memory.ConversationFactory;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import com.xiaozhi.ai.llm.memory.ToolChainPair;
import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.ChatImageBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 纯文本流式对话服务。
 * 负责对话窗口的建立和一轮模型流式调用，不涉及 STT/TTS/Player 等音频组件，也不负责落库。
 */
@Slf4j
@Service
public class TextChatService {

    /** 推给前端的失败原因最长字符数 */
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    /** 看图等待上限，须短于前端流空闲超时（60 秒） */
    private static final Duration VISION_TIMEOUT = Duration.ofSeconds(45);

    @Resource
    private ChatModelFactory chatModelFactory;
    @Resource
    private ConversationFactory conversationFactory;
    @Resource
    private VisionService visionService;

    /**
     * 建立按 sessionId 加载历史与摘要的对话（新会话为空，续接会拉到历史）。
     *
     * @param ownerId   聊天参与者标识
     * @param userId    用户 ID
     * @param role      角色
     * @param sessionId 会话 ID
     */
    public Conversation openConversation(String ownerId, Integer userId, RoleBO role, String sessionId) {
        return conversationFactory.initSessionConversation(ownerId, userId, role, sessionId);
    }

    /**
     * 一轮完整结束时交给调用方落库的内容。
     *
     * @param reply              正文，不含思考过程
     * @param assistantCreatedAt 助手消息时间：摘要按批里最后一条的时间切分历史，挂在对话上的与落库的必须是同一个
     * @param toolChains         本轮放进对话的工具调用链，按顺序落在用户消息与助手回复之间
     */
    public record CompletedTurn(String reply, LocalDateTime assistantCreatedAt, List<ToolChainPair> toolChains) {
    }

    /**
     * 流式对话一轮：调用时即取模型并把用户消息放进对话，返回包含思考过程和正式回复的 ChatToken 流。
     * 带图时角色模型能看就直接给它，否则先由视觉模型看、结果以调用链放进对话；
     * 模型调用失败时以一条 error token 正常结束流；正常完成且正文非空才补上助手消息并回调，
     * 其余情况（报错、取消、空回复）把本轮的用户消息与调用链从对话里摘掉。
     *
     * @param images 随这句话发来的图片，没有给空列表
     */
    public Flux<ChatToken> streamTurn(Conversation conversation, RoleBO role, String userText, List<ChatImageBO> images,
                                      LocalDateTime userCreatedAt, Consumer<CompletedTurn> onTurnCompleted) {
        ChatModel chatModel = chatModelFactory.getChatModel(role);

        // 裸文本 UserMessage + 时间戳 metadata；Conversation 投影层会在送 LLM 前拼出 [时间戳] 文本 的前缀。
        // 无 speaker/emotion，故不挂 MessageMetadataBO。
        Instant userInstant = DateUtils.toInstant(userCreatedAt);
        UserMessage userMessage = new UserMessage(userText);
        MessageTimeMetadata.setTimeMillis(userMessage, userInstant);
        conversation.add(userMessage);

        List<ChatImageBO> direct = !images.isEmpty() && chatModelFactory.resolveConfig(role).acceptsImages() ? images : List.of();
        boolean relay = !images.isEmpty() && direct.isEmpty();
        Mono<List<ToolChainPair>> looked = !relay
                ? Mono.just(List.of())
                : Mono.fromCallable(() -> List.of(lookedAt(userText, visionService.describe(images, visionQuestion(userText)))))
                        .subscribeOn(Schedulers.boundedElastic())
                        .timeout(VISION_TIMEOUT, Mono.error(() -> new IllegalStateException("看图超时，请稍后再试")));

        Flux<ChatToken> preparing = relay
                ? Flux.just(ChatToken.activity(ChatToken.ACTIVITY_VISION))
                : Flux.empty();

        StringBuilder fullResponse = new StringBuilder();
        AtomicBoolean turnCompleted = new AtomicBoolean(false);
        AtomicReference<Usage> usage = new AtomicReference<>();
        AtomicReference<List<ToolChainPair>> added = new AtomicReference<>(List.of());

        Flux<ChatToken> answer = looked.flatMapMany(chains -> {
                    added.set(chains);
                    for (ToolChainPair pair : chains) {
                        addToolChain(conversation, pair, userInstant);
                    }
                    Prompt prompt = promptOf(conversation, direct);
                    return toChatTokens(chatModel.stream(prompt).doOnNext(response -> {
                        // 流式用量一般只在最后一块返回，只留带输入 token 的那块
                        Usage chunkUsage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
                        if (chunkUsage != null && chunkUsage.getPromptTokens() != null && chunkUsage.getPromptTokens() > 0) {
                            usage.set(chunkUsage);
                        }
                    }));
                })
                .doOnNext(token -> {
                    // 只累积正式回复内容，思考过程不持久化
                    if (token.isContent()) {
                        fullResponse.append(token.text());
                    }
                })
                // 必须在 onErrorResume 之前：之后的完成信号可能来自错误恢复，半截正文不能算一轮
                .doOnComplete(() -> {
                    if (fullResponse.isEmpty()) {
                        return;
                    }
                    String reply = fullResponse.toString();
                    LocalDateTime assistantCreatedAt = DateUtils.now();
                    // 挂上本轮用量，对话据此判断上下文是否超限
                    AssistantMessage assistantMessage = AssistantMessage.builder()
                            .content(reply)
                            .properties(usage.get() != null ? Map.of(ChatMemory.USAGE_KEY, usage.get()) : Map.of())
                            .build();
                    MessageTimeMetadata.setTimeMillis(assistantMessage,
                            DateUtils.toInstant(assistantCreatedAt));
                    conversation.add(assistantMessage);
                    turnCompleted.set(true);
                    onTurnCompleted.accept(new CompletedTurn(reply, assistantCreatedAt, List.copyOf(added.get())));
                });

        // 正常完成且配上 AssistantMessage 才算一轮完整对话；客户端中途 abort、
        // LLM 报错、或者拿到空回复，都要把孤立的 UserMessage 与调用链摘掉，
        // 否则下一轮拼 Prompt 时会带着这段没有回复的历史，污染上下文。
        Runnable dropIncompleteTurn = () -> {
            if (!turnCompleted.get()) {
                conversation.remove(userMessage);
                for (ToolChainPair pair : added.get()) {
                    conversation.remove(pair.toolCallMessage());
                    conversation.remove(pair.toolResponseMessage());
                }
            }
        };
        return preparing.concatWith(answer)
                .doOnError(e -> log.error("文本聊天流式响应失败: sessionId={}", conversation.sessionId(), e))
                // 失败原因以 error token 推给前端，不混进正文，也不会被当成助手回复记进对话
                .onErrorResume(e -> Flux.just(ChatToken.error(describeFailure(e))))
                // 在结束信号传下去之前收拾：看图跑在别的线程上，放到 doFinally 里调用方拿到结束时对话可能还没收拾完
                .doOnTerminate(dropIncompleteTurn)
                .doOnCancel(dropIncompleteTurn);
    }

    /** 让视觉模型围绕用户的话看图 */
    private static String visionQuestion(String userText) {
        return "用户发来图片并说：" + userText + "\n请仔细看图，描述与这句话相关的内容；图里有文字的照原样写出。";
    }

    /** 看图结果包成调用链 */
    private static ToolChainPair lookedAt(String userText, String description) {
        AssistantMessage call = AssistantMessage.builder()
                .content("查看用户发来的图片")
                .properties(Map.of())
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "viewImage", "function", "viewImage",
                        ModelOptionsUtils.toJsonString(Map.of("question", userText)))))
                .build();
        ToolResponseMessage result = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("viewImage", "viewImage", description)))
                .build();
        return new ToolChainPair(call, result);
    }

    /**
     * 调用链发生在用户消息之后、助手回复之前，挂上用户消息的时间：
     * 摘要切分时不会被兜底成 now()，也与落库时间一致
     */
    private static void addToolChain(Conversation conversation, ToolChainPair pair, Instant userInstant) {
        MessageTimeMetadata.setTimeMillis(pair.toolCallMessage(), userInstant);
        MessageTimeMetadata.setTimeMillis(pair.toolResponseMessage(), userInstant);
        conversation.addToolCallChain(pair.toolCallMessage(), pair.toolResponseMessage());
    }

    /** 直接看的图只挂在这一轮的用户消息上，对话里不留 */
    private static Prompt promptOf(Conversation conversation, List<ChatImageBO> images) {
        // 文本聊天无位置
        List<Message> messages = new ArrayList<>(conversation.messages(ConversationContext.EMPTY));
        if (!images.isEmpty()) {
            attachImages(messages, images);
        }
        return new Prompt(messages);
    }

    private static void attachImages(List<Message> messages, List<ChatImageBO> images) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage asked) {
                messages.set(i, UserMessage.builder()
                        .text(asked.getText())
                        .metadata(asked.getMetadata())
                        .media(images.stream().map(VisionService::mediaOf).toList())
                        .build());
                return;
            }
        }
    }

    /**
     * 将 ChatResponse 流转换为 ChatToken 流，包含思考内容和正式回复。
     * 同一块里思考内容在前、正文在后；没有 result 或 output 的块直接跳过。
     * <p>
     * Spring AI 1.1.0+ 中，启用 reasoningEffort 后，推理内容通过
     * {@code AssistantMessage.getProperties().get("reasoningContent")} 返回。
     */
    public static Flux<ChatToken> toChatTokens(Flux<ChatResponse> responses) {
        return responses.mapNotNull(ChatResponse::getResult)
                .mapNotNull(Generation::getOutput)
                .flatMap(message -> {
                    List<ChatToken> tokens = new ArrayList<>();
                    Object reasoning = message.getMetadata().get("reasoningContent");
                    if (reasoning instanceof String r && !r.isEmpty()) {
                        tokens.add(ChatToken.thinking(r));
                    }
                    String text = message.getText();
                    if (text != null && !text.isEmpty()) {
                        tokens.add(ChatToken.content(text));
                    }
                    return Flux.fromIterable(tokens);
                });
    }

    /**
     * 取模型调用失败的原因给前端展示：服务商返回了错误响应时带上状态码与响应体，
     * 否则取异常链最底层的描述；过长时截断，避免整页 HTML 错误页灌进聊天气泡。
     */
    static String describeFailure(Throwable error) {
        String deepestMessage = null;
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException webError) {
                return truncateFailureReason(
                        "HTTP " + webError.getStatusCode().value() + " " + webError.getResponseBodyAsString());
            }
            if (t instanceof RestClientResponseException restError) {
                return truncateFailureReason(
                        "HTTP " + restError.getStatusCode().value() + " " + restError.getResponseBodyAsString());
            }
            if (StringUtils.hasText(t.getMessage())) {
                deepestMessage = t.getMessage();
            }
        }
        return truncateFailureReason(deepestMessage != null ? deepestMessage : error.getClass().getSimpleName());
    }

    private static String truncateFailureReason(String reason) {
        String trimmed = reason.strip();
        return trimmed.length() <= MAX_FAILURE_REASON_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_FAILURE_REASON_LENGTH) + "…";
    }
}

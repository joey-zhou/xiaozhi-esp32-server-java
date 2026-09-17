package com.xiaozhi.ai.llm.service;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.llm.memory.ChatMemory;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.ConversationContext;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import com.xiaozhi.ai.llm.memory.MessageWindowConversation;
import com.xiaozhi.common.model.ChatToken;
import com.xiaozhi.common.model.bo.RoleBO;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
/**
 * 纯文本流式对话服务。
 * 负责对话窗口的建立和一轮模型流式调用，不涉及 STT/TTS/Player 等音频组件，也不负责落库。
 */
@Slf4j
@Service
public class TextChatService {

    /** 推给前端的失败原因最长字符数 */
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    @Resource
    private ChatModelFactory chatModelFactory;
    @Resource
    private ChatMemory chatMemory;

    // 独立的 key：Web 聊天场景单独调优，不和设备侧窗口/长期/摘要三种记忆策略的 max-messages 共用配置项
    @Value("${conversation.web-chat.max-messages:16}")
    private int maxMessages;

    /**
     * 建立按 sessionId 加载历史的消息窗口对话（新会话为空，续接会拉到历史）。
     *
     * @param ownerId   聊天参与者标识
     * @param userId    用户 ID
     * @param role      角色
     * @param sessionId 会话 ID
     */
    public Conversation openConversation(String ownerId, Integer userId, RoleBO role, String sessionId) {
        return MessageWindowConversation.builder()
                .chatMemory(chatMemory)
                .maxMessages(maxMessages)
                .ownerId(ownerId)
                .roleId(role.getRoleId())
                .roleDesc(role.getRoleDesc())
                .userId(userId)
                .sessionId(sessionId)
                .sessionScoped(true)
                .build();
    }

    /**
     * 流式对话一轮：调用时即取模型并把用户消息放进对话，返回包含思考过程和正式回复的 ChatToken 流。
     * 模型调用失败时以一条 error token 正常结束流；正常完成且正文非空才补上助手消息并回调，
     * 其余情况（报错、取消、空回复）把本轮的用户消息从对话里摘掉。
     *
     * @param conversation    本会话的对话窗口
     * @param role            本轮使用的角色
     * @param userText        用户输入文本
     * @param userCreatedAt   用户消息时间
     * @param onTurnCompleted 一轮完整结束时回调，入参是正文（不含思考过程）
     */
    public Flux<ChatToken> streamTurn(Conversation conversation, RoleBO role, String userText,
                                      LocalDateTime userCreatedAt, Consumer<String> onTurnCompleted) {
        ChatModel chatModel = chatModelFactory.getChatModel(role);

        // 裸文本 UserMessage + 时间戳 metadata；Conversation 投影层会在送 LLM 前拼出 [时间戳] 文本 的前缀。
        // 无 speaker/emotion，故不挂 MessageMetadataBO。
        Instant userInstant = userCreatedAt.atZone(ZoneId.systemDefault()).toInstant();
        UserMessage userMessage = new UserMessage(userText);
        MessageTimeMetadata.setTimeMillis(userMessage, userInstant);
        conversation.add(userMessage);

        // 文本聊天无位置
        List<Message> messages = conversation.messages(ConversationContext.EMPTY);

        Prompt prompt = new Prompt(messages);

        StringBuilder fullResponse = new StringBuilder();
        AtomicBoolean turnCompleted = new AtomicBoolean(false);

        return toChatTokens(chatModel.stream(prompt))
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
                    conversation.add(new AssistantMessage(reply));
                    turnCompleted.set(true);
                    onTurnCompleted.accept(reply);
                })
                .doOnError(e -> log.error("文本聊天流式响应失败: sessionId={}", conversation.sessionId(), e))
                // 失败原因以 error token 推给前端，不混进正文，也不会被当成助手回复记进对话
                .onErrorResume(e -> Flux.just(ChatToken.error(describeFailure(e))))
                .doFinally(signalType -> {
                    // 正常完成且配上 AssistantMessage 才算一轮完整对话；客户端中途 abort、
                    // LLM 报错、或者拿到空回复，都要把孤立的 UserMessage 摘掉，
                    // 否则下一轮拼 Prompt 时会带着这条没有回复的历史消息，污染上下文。
                    if (!turnCompleted.get()) {
                        conversation.remove(userMessage);
                    }
                });
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

package com.xiaozhi.ai.llm.providers;

import io.github.imfangs.dify.client.DifyChatClient;
import io.github.imfangs.dify.client.DifyClientFactory;
import io.github.imfangs.dify.client.callback.ChatStreamCallback;
import io.github.imfangs.dify.client.enums.ResponseMode;
import io.github.imfangs.dify.client.event.*;
import io.github.imfangs.dify.client.model.chat.ChatMessage;
import io.github.imfangs.dify.client.model.chat.ChatMessageResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DifyChatModel implements ChatModel {
    /**
     * Persona 在 ToolContext 中放入的 sessionId 键，与
     * {@code com.xiaozhi.dialogue.runtime.Persona.TOOL_CONTEXT_SESSION_ID_KEY} 保持一致。
     * 此处用字面量是因为 xiaozhi-ai 模块不依赖 xiaozhi-dialogue。
     */
    private static final String TOOL_CONTEXT_SESSION_ID_KEY = "sessionId";

    private DifyChatClient chatClient;

    /**
     * 按 sessionId 缓存 Dify 返回的 conversation_id，使多轮对话能延续 Dify 智能体侧的会话记忆。
     */
    private final Cache<String, String> conversationIds = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(24))
            .maximumSize(5000)
            .build();

    /**
     * 构造函数
     *
     * @param endpoint  API端点
     * @param apiKey    API密钥
     */
    public DifyChatModel(String endpoint, String apiKey) {
        chatClient = DifyClientFactory.createChatClient(endpoint, apiKey);
    }

    public String getProviderName() {
        return "dify";
    }

    @Override
    public ChatResponse call(Prompt prompt) {

        // 创建聊天消息
        // inputs 必须为非 null（即使没有 App 变量也要传空对象），否则 Dify 服务端会拒绝请求。
        // conversationId 用上一轮 Dify 返回的会话 ID，使智能体能延续上下文记忆。
        ChatMessage message = ChatMessage.builder()
                .query(prompt.getContents())
                .inputs(Map.of())
                .user(resolveUserId(prompt))
                .conversationId(getCurrentConversationId(prompt))
                .responseMode(ResponseMode.BLOCKING)
                .build();
        try {
            // 发送消息并获取响应
            ChatMessageResponse response = chatClient.sendChatMessage(message);
            log.debug("回复: {}", response.getAnswer());
            log.debug("会话ID: {}", response.getConversationId());
            log.debug("消息ID: {}", response.getMessageId());
            saveCurrentConversationId(prompt, response.getConversationId());
            return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                    .content(response.getAnswer())
                    .properties(Map.of("messageId", response.getMessageId(), "conversationId", response.getConversationId()))
                    .build())));

        } catch (IOException e) {
            log.error("错误: ", e);
            return ChatResponse.builder().generations(Collections.emptyList()).build();
        }

    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Flux<ChatResponse> responseFlux = Flux.create(sink -> {

            // inputs 必须为非 null（即使没有 App 变量也要传空对象），否则 Dify 服务端会拒绝请求。
            // conversationId 用上一轮 Dify 返回的会话 ID，使智能体能延续上下文记忆。
            String userId = resolveUserId(prompt);
            ChatMessage message = ChatMessage.builder()
                    .user(userId)
                    .query(prompt.getUserMessage().getText())
                    .inputs(Map.of())
                    .conversationId(getCurrentConversationId(prompt))
                    .responseMode(ResponseMode.STREAMING)
                    .build();

            // 用户打断时调用 Dify 的停止生成接口，不让已经不需要的响应继续占用上游的算力配额
            AtomicReference<String> taskId = new AtomicReference<>();
            sink.onCancel(() -> {
                String id = taskId.get();
                if (id != null) {
                    try {
                        chatClient.stopChatMessage(id, userId);
                    } catch (Exception e) {
                        log.warn("停止Dify对话失败: taskId={}", id, e);
                    }
                }
            });

            // 发送流式消息
            try {
                chatClient.sendChatMessageStream(message, new ChatStreamCallback() {
                    @Override
                    public void onMessage(MessageEvent event) {
                        taskId.set(event.getTaskId());
                        sink.next(ChatResponse.builder()
                                .generations(
                                        List.of(new Generation(AssistantMessage.builder()
                                                .content(event.getAnswer())
                                                .properties(Map.of("messageId", event.getMessageId(),
                                                        "conversationId", event.getConversationId()))
                                                .build())))
                                .build());
                    }

                    @Override
                    public void onAgentMessage(AgentMessageEvent event) {
                        taskId.set(event.getTaskId());
                        sink.next(ChatResponse.builder()
                                .generations(
                                        List.of(new Generation(AssistantMessage.builder()
                                                .content(event.getAnswer())
                                                .properties(Map.of("messageId", event.getMessageId(),
                                                        "conversationId", event.getConversationId()))
                                                .build())))
                                .build());
                    }

                    @Override
                    public void onMessageEnd(MessageEndEvent event) {
                        // 必须先持久化 conversationId 再 complete，避免下一轮 chatStream 在 save 之前就读取到旧值。
                        saveCurrentConversationId(prompt, event.getConversationId());
                        sink.complete();
                    }

                    @Override
                    public void onError(ErrorEvent event) {
                        sink.error(new IOException(event.toString()));
                    }

                    @Override
                    public void onException(Throwable throwable) {
                        log.error("异常: {}", throwable.getMessage());
                        sink.error(throwable);
                    }

                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return responseFlux;
    }

    /**
     * 从Prompt的ChatOptions中提取设备ID，生成确定性的用户ID。
     * 如果无法提取设备ID，则回退到基于UUID的用户ID。
     */
    private String resolveUserId(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
            Map<String, Object> toolContext = toolCallingChatOptions.getToolContext();
            if (toolContext != null) {
                Object deviceIdObj = toolContext.get("deviceId");
                if (deviceIdObj instanceof String deviceId && !deviceId.isBlank()) {
                    return "user_xz_" + deviceId.replace(":", "");
                }
            }
        }
        return "user_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 从 ToolContext 取出 sessionId，回查上一轮 Dify 返回的 conversation_id。
     * 拿不到时返回 null：Dify 接口将其视为开启全新会话。
     */
    private String getCurrentConversationId(Prompt prompt) {
        String sessionId = extractSessionId(prompt);
        if (sessionId == null) {
            return null;
        }
        return conversationIds.getIfPresent(sessionId);
    }

    /**
     * 持久化 Dify 返回的 conversation_id。仅在拿到有效 sessionId 与 conversationId 时写入。
     */
    private void saveCurrentConversationId(Prompt prompt, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        String sessionId = extractSessionId(prompt);
        if (sessionId == null) {
            return;
        }
        conversationIds.put(sessionId, conversationId);
    }

    private String extractSessionId(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
            Map<String, Object> toolContext = toolCallingChatOptions.getToolContext();
            if (toolContext != null) {
                Object value = toolContext.get(TOOL_CONTEXT_SESSION_ID_KEY);
                if (value instanceof String sessionId && !sessionId.isBlank()) {
                    return sessionId;
                }
            }
        }
        return null;
    }
}
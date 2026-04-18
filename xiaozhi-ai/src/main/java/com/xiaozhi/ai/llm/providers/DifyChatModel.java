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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DifyChatModel implements ChatModel {
    private DifyChatClient chatClient;
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
        ChatMessage message = ChatMessage.builder()
                .query(prompt.getContents())
                .user(resolveUserId(prompt))
                .responseMode(ResponseMode.BLOCKING)
                .build();
        try {
            // 发送消息并获取响应
            ChatMessageResponse response = chatClient.sendChatMessage(message);
            log.debug("回复: {}", response.getAnswer());
            log.debug("会话ID: {}", response.getConversationId());
            log.debug("消息ID: {}", response.getMessageId());
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

            ChatMessage message = ChatMessage.builder()
                    .user(resolveUserId(prompt))
                    .query(prompt.getUserMessage().getText())
                    .responseMode(ResponseMode.STREAMING)
                    .build();

            // 发送流式消息
            try {
                chatClient.sendChatMessageStream(message, new ChatStreamCallback() {
                    @Override
                    public void onMessage(MessageEvent event) {
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
                        // 通知完成
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
}
package com.xiaozhi.dialogue.llm.providers;

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
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DifyChatModel implements ChatModel {
    private DifyChatClient chatClient;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DifyChatModel.class);

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
                .user("user-123") // TODO 用户ID,通过Options传入。
                .responseMode(ResponseMode.BLOCKING)
                .build();
        try {
            // 发送消息并获取响应
            ChatMessageResponse response = chatClient.sendChatMessage(message);
            logger.debug("回复: {}", response.getAnswer());
            logger.debug("会话ID: {}", response.getConversationId());
            logger.debug("消息ID: {}", response.getMessageId());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(response.getAnswer(), Map.of("messageId", response.getMessageId(), "conversationId", response.getConversationId())))));

        } catch (IOException e) {
            logger.error("错误: ", e);
            return ChatResponse.builder().generations(Collections.emptyList()).build();
        }

    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Flux<ChatResponse> responseFlux = Flux.create(sink -> {

            // 创建唯一的用户ID. TODO 用户ID,通过Options传入。
            String userId = "user_xz_" + UUID.randomUUID().toString().replace("-", "");

            ChatMessage message = ChatMessage.builder()
                    .user(userId)
                    .query(prompt.getUserMessage().getText())
                    .responseMode(ResponseMode.STREAMING)
                    .build();

            // 发送流式消息
            try {
                chatClient.sendChatMessageStream(message, new ChatStreamCallback() {
                    @Override
                    public void onMessage(MessageEvent event) {
                        sink.next(ChatResponse.builder().generations(
                                        List.of(new Generation(new AssistantMessage(event.getAnswer(),
                                                Map.of("messageId", event.getMessageId(),
                                                        "conversationId", event.getConversationId())))))
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
                        logger.error("异常: {}", throwable.getMessage());
                        sink.error(throwable);
                    }

                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return responseFlux;
    }
}
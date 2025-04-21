package com.xiaozhi.websocket.llm.providers;

import com.coze.openapi.client.chat.CreateChatReq;
import com.coze.openapi.client.chat.model.ChatEvent;
import com.coze.openapi.client.chat.model.ChatEventType;
import com.coze.openapi.client.connversations.message.model.Message;
import com.coze.openapi.service.auth.TokenAuth;
import com.coze.openapi.service.service.CozeAPI;
import com.xiaozhi.websocket.llm.api.AbstractLlmService;
import com.xiaozhi.websocket.llm.api.StreamResponseListener;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Coze LLM服务实现
 */
public class CozeService extends AbstractLlmService {

    private final TokenAuth authCli;
    private final CozeAPI coze;
    private final String botId;

    /**
     * 构造函数
     * 
     * @param endpoint  API端点
     * @param appId     应用ID (在Coze中对应botId)
     * @param apiKey    API密钥 (在Coze中不使用)
     * @param apiSecret API密钥 (在Coze中对应access_token)
     * @param model     模型名称 (在Coze中不使用)
     */
    public CozeService(String endpoint, String appId, String apiKey, String apiSecret, String model) {
        super(endpoint, appId, apiKey, apiSecret, model);

        // 使用apiSecret作为access_token
        this.authCli = new TokenAuth(apiSecret);

        // 使用endpoint或默认的Coze API地址
        String baseUrl = "https://api.coze.cn";

        // 初始化Coze API客户端
        this.coze = new CozeAPI.Builder()
                .baseURL(baseUrl)
                .auth(authCli)
                .readTimeout(60000) // 60秒超时
                .build();

        // 使用appId作为botId
        this.botId = model;

        logger.info("初始化Coze服务，botId: {}, baseUrl: {}", botId, baseUrl);
    }

    @Override
    protected String chat(List<Map<String, String>> messages) throws IOException {
        if (messages == null || messages.isEmpty()) {
            throw new IOException("消息列表不能为空");
        }

        // 将消息格式转换为Coze API所需格式
        List<Message> cozeMessages = convertToCozeMessages(messages);

        // 创建唯一的用户ID
        String userId = "user_" + UUID.randomUUID().toString().replace("-", "");

        // 创建聊天请求
        CreateChatReq req = CreateChatReq.builder()
                .botID(botId)
                .userID(userId)
                .messages(cozeMessages)
                .build();

        try {
            // 创建CompletableFuture来处理异步响应
            CompletableFuture<String> future = new CompletableFuture<>();

            // 发送请求并获取流式响应
            StringBuilder fullResponse = new StringBuilder();
            Flowable<ChatEvent> resp = coze.chat().stream(req);

            resp.subscribeOn(Schedulers.io())
                    .subscribe(
                            event -> {
                                if (ChatEventType.CONVERSATION_MESSAGE_DELTA.equals(event.getEvent())) {
                                    String content = event.getMessage().getContent();
                                    if (content != null && !content.isEmpty()) {
                                        fullResponse.append(content);
                                    }
                                }
                                if (ChatEventType.CONVERSATION_CHAT_COMPLETED.equals(event.getEvent())) {
                                    future.complete(fullResponse.toString());
                                }
                            },
                            throwable -> {
                                logger.error("Coze API请求失败: {}", throwable.getMessage(), throwable);
                                future.completeExceptionally(throwable);
                            },
                            () -> {
                                // 如果没有收到CONVERSATION_CHAT_COMPLETED事件，确保完成future
                                if (!future.isDone()) {
                                    future.complete(fullResponse.toString());
                                }
                            });

            // 等待响应，设置超时
            return future.get(60, TimeUnit.SECONDS);

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("获取Coze响应时出错: {}", e.getMessage(), e);
            throw new IOException("获取Coze响应时出错: " + e.getMessage(), e);
        }
    }

    @Override
    protected void chatStream(List<Map<String, String>> messages, StreamResponseListener streamListener)
            throws IOException {
        if (messages == null || messages.isEmpty()) {
            throw new IOException("消息列表不能为空");
        }

        // 通知开始
        streamListener.onStart();

        // 将消息格式转换为Coze API所需格式
        List<Message> cozeMessages = convertToCozeMessages(messages);

        // 创建唯一的用户ID
        String userId = "user_" + UUID.randomUUID().toString().replace("-", "");

        // 创建聊天请求
        CreateChatReq req = CreateChatReq.builder()
                .botID(botId)
                .userID(userId)
                .messages(cozeMessages)
                .build();

        // 保存完整响应
        StringBuilder fullResponse = new StringBuilder();

        // 发送请求
        try {
            Flowable<ChatEvent> resp = coze.chat().stream(req);
            resp.subscribeOn(Schedulers.io())
                    .subscribe(
                            event -> {
                                if (ChatEventType.CONVERSATION_MESSAGE_DELTA.equals(event.getEvent())) {
                                    String content = event.getMessage().getContent();
                                    if (content != null && !content.isEmpty()) {
                                        streamListener.onToken(content);
                                        fullResponse.append(content);
                                    }
                                }
                                if (ChatEventType.CONVERSATION_CHAT_COMPLETED.equals(event.getEvent())) {
                                    // 通知完成
                                    streamListener.onComplete(fullResponse.toString());
                                }
                            },
                            throwable -> {
                                logger.error("流式请求失败: {}", throwable.getMessage(), throwable);
                                streamListener.onError(throwable);
                            },
                            () -> {
                                // 如果没有收到CONVERSATION_CHAT_COMPLETED事件，在这里确保通知完成
                                if (fullResponse.length() > 0 && !fullResponse.toString().isEmpty()) {
                                    streamListener.onComplete(fullResponse.toString());
                                } else {
                                    streamListener.onError(new IOException("未收到有效响应"));
                                }
                            });
        } catch (Exception e) {
            logger.error("创建流式请求时出错: {}", e.getMessage(), e);
            streamListener.onError(e);
        }
    }

    /**
     * 将通用消息格式转换为Coze API所需的消息格式
     * 
     * @param messages 通用格式的消息列表
     * @return Coze格式的消息列表
     */
    private List<Message> convertToCozeMessages(List<Map<String, String>> messages) {
        List<Message> cozeMessages = new ArrayList<>();

        for (Map<String, String> msg : messages) {
            String role = msg.get("role");
            String content = msg.get("content");

            if ("user".equals(role)) {
                cozeMessages.add(Message.buildUserQuestionText(content));
            } else if ("assistant".equals(role)) {
                cozeMessages.add(Message.buildAssistantAnswer(content));
            } else if ("system".equals(role)) {
                // coze 系统提示默认不在这里设定，需要在 coze 中设定
            }
        }

        return cozeMessages;
    }

    @Override
    public String getProviderName() {
        return "coze";
    }
}
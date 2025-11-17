package com.xiaozhi.dialogue.llm.providers;

import cn.hutool.core.bean.BeanUtil;
import com.coze.openapi.client.chat.*;
import com.coze.openapi.client.chat.model.*;
import com.coze.openapi.client.connversations.message.model.Message;
import com.coze.openapi.client.connversations.message.model.MessageType;
import com.coze.openapi.service.auth.TokenAuth;
import com.coze.openapi.service.service.CozeAPI;

import io.reactivex.Flowable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Coze LLM服务实现
 */
public class CozeChatModel implements ChatModel {

    private final CozeAPI coze;
    private final String botId;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    public static final String PROVIDER_NAME = "coze";


    /**
     * 构造函数
     * @param apiSecret Coze API密钥
     * @param model     模型名称 (在Coze中不使用)
     */
    public CozeChatModel(String apiSecret, String model) {

        // 使用apiSecret作为access_token
        TokenAuth authCli = new TokenAuth(apiSecret);

        // 使用endpoint或默认的Coze API地址
        String baseUrl = "https://api.coze.cn";

        // 初始化Coze API客户端
        this.coze = new CozeAPI.Builder()
                .baseURL(baseUrl)
                .auth(authCli)
                .readTimeout(60000) // 60秒超时
                .build();

        // 数据库coze相关的配置行，其字段里的appId已用作token的作用，configName字段实际作为coze 的botId。也相当于入参的model。
        this.botId = model;

        logger.info("初始化Coze服务，botId: {}, baseUrl: {}", botId, baseUrl);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        var messages = prompt.getInstructions();
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("消息列表不能为空");
        }

        // 将消息格式转换为Coze API所需格式
        List<Message> cozeMessages = convertToCozeMessages(messages);

        // 创建唯一的用户ID
        // TODO 这个UserID，在Coze里是否用来标识最终端的用户？如果是，那可以用deviceID。通过Prompt的Options传入userID。
        String userId = "user_" + UUID.randomUUID().toString().replace("-", "");

        // 创建聊天请求
        CreateChatReq req = CreateChatReq.builder()
                .botID(botId)
                .userID(userId)
                .messages(cozeMessages)
                .build();

        CreateChatResp chatResp = coze.chat().create(req);
        // Chat chat = chatResp.getChat();
        // get chat id and conversationID
        // String chatID = chat.getID();
        // String conversationID = chat.getConversationID();
        /*
         * Step two, poll the result of chat
         * Assume the development allows at most one chat to run for 10 seconds. If it
         * exceeds 10 seconds,
         * the chat will be cancelled.
         * And when the chat status is not completed, poll the status of the chat once
         * every second.
         * After the chat is completed, retrieve all messages in the chat.
         */
        long timeout = 10L;
        long start = System.currentTimeMillis();

        // the developer can also set the timeout.
        try {
            ChatPoll chatPoll = coze.chat().createAndPoll(req, timeout);
            logger.debug(chatPoll.toString());
            var message = chatPoll.getMessages().getLast();
            Map<String, Object> messageMetadata = Optional.ofNullable(message.getMetaData())
                    .map(metaData -> new HashMap<String, Object>(metaData))
                    .orElse(new HashMap<>());
            var assistantMessage = new AssistantMessage(message.getContent(), messageMetadata);
            var generation = new Generation(assistantMessage,
                    ChatGenerationMetadata.builder().metadata(BeanUtil.beanToMap(chatPoll.getChat())).build());
            logger.info("耗时：{}ms", System.currentTimeMillis() - start);
            return ChatResponse.builder().generations(List.of(generation)).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        var messages = prompt.getInstructions();
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("消息列表不能为空");
        }

        // 将消息格式转换为Coze API所需格式
        List<Message> cozeMessages = convertToCozeMessages(messages);

        // 创建唯一的用户ID TODO 这个用户ID需要优化，需要根据设备ID生成。可以通过Options或者构造函数传入
        // String userId = "user_xz_" + modelContext.getDeviceId().replace(":", "");
        String userId = "user_" + UUID.randomUUID().toString().replace("-", "");
        // 创建聊天请求
        CreateChatReq req = CreateChatReq.builder()
                .botID(botId)
                .userID(userId)
                .messages(cozeMessages)
                .build();

        // 发送请求
        try {
            Flowable<ChatEvent> resp = coze.chat().stream(req);
            // 转换为 Reactor Flux
            Flux<ChatEvent> flux = Flux.from(resp);

            Flux<ChatResponse> chatResponse = flux
                    .filter(event -> event != null) // 过滤掉 null 事件
                    .map(event -> {
                        List<AssistantMessage.ToolCall> toolCalls = List.of();
                        String content = "";

                        if (ChatEventType.CONVERSATION_MESSAGE_DELTA.equals(event.getEvent())) {
                            Message message = event.getMessage();
                            content = Optional.ofNullable(message)
                                    .map(Message::getContent)
                                    .orElse("");
                        }

                        if (ChatEventType.CONVERSATION_CHAT_REQUIRES_ACTION.equals(event.getEvent())) {
                            List<ChatToolCall> toolCallList = event.getChat().getRequiredAction()
                                    .getSubmitToolOutputs().getToolCalls();

                            toolCalls = toolCallList
                                    .stream()
                                    .map(toolCall -> new AssistantMessage.ToolCall(
                                            toolCall.getID(),
                                            "function",
                                            toolCall.getFunction().getName(),
                                            toolCall.getFunction().getArguments()))
                                    .toList();
                        }

                        if (ChatEventType.CONVERSATION_CHAT_COMPLETED.equals(event.getEvent())) {
                            Message message = event.getMessage();
                            if (message != null && MessageType.FOLLOW_UP.equals(message.getType())) {
                                logger.debug(message.getContent());
                            } else if (event.getChat() != null && event.getChat().getUsage() != null) {
                                logger.debug("Token usage:{}", event.getChat().getUsage().getTokenCount());
                            }
                        }

                        if (ChatEventType.DONE.equals(event.getEvent())) {
                            coze.shutdownExecutor();
                        }

                        var message = event.getMessage();

                        Map<String, Object> messageMetadata = Optional.ofNullable(message)
                                .map(Message::getMetaData)
                                .map(metaData -> {
                                    Map<String, Object> result = new HashMap<>();
                                    if (metaData != null) {
                                        result.putAll(metaData);
                                    }
                                    return result;
                                })
                                .orElse(new HashMap<>());

                        var assistantMessage = new AssistantMessage(content, messageMetadata, toolCalls);

                        Map<String, Object> chatMetadata = Optional.ofNullable(event.getChat())
                                .map(chat -> {
                                    Map<String, Object> beanMap = BeanUtil.beanToMap(chat);
                                    // 过滤掉 null 值
                                    return beanMap.entrySet().stream()
                                            .filter(entry -> entry.getValue() != null)
                                            .collect(Collectors.toMap(
                                                    Map.Entry::getKey,
                                                    Map.Entry::getValue));
                                })
                                .orElse(new HashMap<>());

                        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                                .metadata(chatMetadata)
                                .build();

                        var generation = new Generation(assistantMessage, generationMetadata);
                        return new ChatResponse(List.of(generation));
                    });

            final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
                    .prompt(prompt)
                    .provider(PROVIDER_NAME)
                    .build();

            return new MessageAggregator().aggregate(chatResponse, observationContext::setResponse);
        } catch (Exception e) {
            logger.error("创建流式请求时出错: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 将通用消息格式转换为Coze API所需的消息格式
     * 
     * @param messages 通用格式的消息列表
     * @return Coze格式的消息列表
     */
    private List<Message> convertToCozeMessages(List<org.springframework.ai.chat.messages.Message> messages) {
        List<Message> cozeMessages = new ArrayList<>();

        for (org.springframework.ai.chat.messages.Message msg : messages) {
            Map<String, String> metadata = msg.getMetadata().entrySet()
                    .stream()
                    .filter(e -> e.getValue() != null)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().toString()));
            switch (msg.getMessageType()) {
                case USER:
                    cozeMessages.add(Message.buildUserQuestionText(msg.getText(), metadata));
                    break;
                case ASSISTANT:
                    cozeMessages.add(Message.buildAssistantAnswer(msg.getText(), metadata));
                    break;
                default:
                    // coze 系统提示默认不在这里设定，需要在 coze 中设定
            }
        }

        return cozeMessages;
    }

}
package com.xiaozhi.dialogue.llm;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.api.StreamResponseListener;
import com.xiaozhi.dialogue.llm.api.TokenStreamResponseListener;
import com.xiaozhi.dialogue.llm.api.TriConsumer;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.dialogue.llm.memory.ChatMemory;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * 负责管理和协调LLM相关功能
 * TODO 重构：改成Domain Entity: ChatRole(聊天角色)，管理对话历史记录，管理对话工具调用等。
 */
@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    public static final String TOOL_CONTEXT_SESSION_KEY = "session";



    @Resource
    private ChatMemory chatMemoryStore;

    // TODO 移到构建者模式，由连接通过认证，可正常对话时，创建实例，构建好一个完整的Role.
    @Resource
    private ChatModelFactory chatModelFactory;

    /**
     * 处理用户查询（同步方式）
     * 
     * @param session         会话信息
     * @param message         用户消息
     * @param useFunctionCall 是否使用函数调用
     * @return 模型回复
     */
    public String chat(ChatSession session, String message, boolean useFunctionCall) {
        try {

            // 获取ChatModel
            ChatModel chatModel = chatModelFactory.takeChatModel(session);

            ChatOptions chatOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(useFunctionCall ? session.getToolCallbacks() : new ArrayList<>())
                    .toolContext(TOOL_CONTEXT_SESSION_KEY, session)
                    .build();

            UserMessage userMessage = new UserMessage(message);
            List<Message> messages = session.getConversation().prompt(userMessage);
            Prompt prompt = new Prompt(messages,chatOptions);

            ChatResponse chatResponse = chatModel.call(prompt);
            if (chatResponse == null || chatResponse.getResult().getOutput().getText() == null) {
                logger.warn("模型响应为空或无生成内容");
                return "抱歉，我在处理您的请求时遇到了问题。请稍后再试。";
            }
            AssistantMessage assistantMessage =chatResponse.getResult().getOutput();

            Thread.startVirtualThread(() -> {// 异步持久化
                // 保存AI消息，会被持久化至数据库。
                session.getConversation().addMessage(userMessage,session.getUserTimeMillis(),assistantMessage,session.getAssistantTimeMillis());
            });
            return assistantMessage.getText();

        } catch (Exception e) {
            logger.error("处理查询时出错: {}", e.getMessage(), e);
            return "抱歉，我在处理您的请求时遇到了问题。请稍后再试。";
        }
    }

    /**
     * 处理用户查询（流式方式）
     *
     * @param message         用户消息
     * @param useFunctionCall 是否使用函数调用
     */
    public Flux<ChatResponse> chatStream(ChatSession session, String message,
            boolean useFunctionCall) {
        logger.info("start chat stream");
        // 获取ChatModel
        ChatModel chatModel = chatModelFactory.takeChatModel(session);

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(useFunctionCall ? session.getToolCallbacks() : new ArrayList<>())
                .toolContext(TOOL_CONTEXT_SESSION_KEY, session)
                .build();

        UserMessage userMessage = new UserMessage(message);
        List<Message> messages = session.getConversation().prompt(userMessage);
        Prompt prompt = new Prompt(messages, chatOptions);

        // 调用实际的流式聊天方法
        Flux<ChatResponse> chatResponseFlux = chatModel.stream(prompt);
        logger.info("end chat stream");
        return chatResponseFlux;
    }


    public void chatStreamBySentence(ChatSession session, String message, boolean useFunctionCall,
                                     TriConsumer<String, Boolean, Boolean> sentenceHandler) {
        try {
            // 创建流式响应监听器（明确变量名）
            StreamResponseListener streamListener = new TokenStreamResponseListener(session, message, sentenceHandler);
            // 用于存储工具名称（添加final修饰，明确不可变引用）
            final StringBuilder toolNameBuilder = new StringBuilder();

            // 调用流式接口并订阅结果
            chatStream(session, message, useFunctionCall)
                    .subscribe(
                            // 处理每个响应token
                            chatResponse -> processChatResponse(chatResponse, streamListener, toolNameBuilder, useFunctionCall),
                            // 处理错误
                            error -> handleStreamError(error, streamListener),
                            // 处理完成
                            () -> streamListener.onComplete(toolNameBuilder.toString())
                    );
        } catch (Exception e) {
            logger.error("处理LLM流式请求时发生异常", e);
            sentenceHandler.accept("抱歉，处理您的请求时遇到问题，请稍后再试。", true, true);
        }
    }

    /**
     * 处理单个聊天响应对象，提取token和工具名称
     */
    private void processChatResponse(ChatResponse chatResponse,
                                     StreamResponseListener streamListener,
                                     StringBuilder toolNameBuilder,
                                     boolean useFunctionCall) {
        // 安全提取token（减少嵌套判断，提前返回）
        String token = extractTokenFromResponse(chatResponse);
        if (!token.isEmpty()) {
            streamListener.onToken(token);
        }

        // 日志优化：使用参数化日志，避免字符串拼接开销
        logger.info("toolName={}, useFunctionCall={}, token={}",
                toolNameBuilder, useFunctionCall, token);

        // 仅在需要函数调用且工具名未提取时处理
        if (useFunctionCall && toolNameBuilder.length() == 0) {
            extractToolNameFromResponse(chatResponse, toolNameBuilder);
        }
    }

    /**
     * 从响应中提取token（单一职责）
     */
    private String extractTokenFromResponse(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        return chatResponse.getResult().getOutput() != null ? chatResponse.getResult().getOutput().getText() : "";
    }

    /**
     * 从响应元数据中提取工具名称
     */
    private void extractToolNameFromResponse(ChatResponse chatResponse, StringBuilder toolNameBuilder) {
        Generation generation = chatResponse.getResult();
        if (generation == null) {
            return;
        }

        ChatGenerationMetadata metadata = generation.getMetadata();
        if (metadata == null) {
            logger.debug("响应元数据为空，无法提取工具名称");
            return;
        }

        String toolName = metadata.get("toolName");
        if (toolName != null && !toolName.isEmpty()) {
            toolNameBuilder.append(toolName);
            logger.debug("成功提取工具名称: {}", toolName);
        }
    }

    /**
     * 处理流式错误（统一错误处理）
     */
    private void handleStreamError(Throwable error, StreamResponseListener streamListener) {
        logger.error("流式响应处理出错", error);
        streamListener.onError(error);
    }


    /**
     * 清除设备缓存
     * 
     * @param deviceId 设备ID
     */
    public void clearMessageCache(String deviceId) {
        chatMemoryStore.clearMessages(deviceId);
    }
}
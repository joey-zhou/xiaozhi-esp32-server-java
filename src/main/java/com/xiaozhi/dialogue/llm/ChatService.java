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
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * 负责管理和协调LLM相关功能
 * 未来考虑：改成Domain Entity: ChatRole(聊天角色)，管理对话历史记录，管理对话工具调用等。
 * 未来考虑：在连接通过认证，可正常对话时，创建实例，构建好一个完整的Role。
 */
@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    public static final String TOOL_CONTEXT_SESSION_KEY = "session";




    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private McpSessionManager mcpSessionManager;

    @Resource
    private ToolCallingManager toolCallingManager;
    
    @Resource
    private SessionManager sessionManager;
    
    @Resource
    private ApplicationContext applicationContext;

    /**
     * 从流式响应中提取工具名称
     * 
     * @param session 聊天会话
     * @param message 用户消息
     * @param useFunctionCall 是否使用函数调用
     * @param chatResponses 收集的所有ChatResponse
     * @param fullResponse 完整的响应文本
     * @param hasToolCalls 是否有工具调用的标记
     * @return 提取到的工具名称，如果没有找到则返回空字符串
     */
    private String getToolName(ChatSession session, String message, boolean useFunctionCall,
                              List<ChatResponse> chatResponses, String fullResponse, boolean hasToolCalls) {
        StringBuilder toolName = new StringBuilder();
        
        // 在流式响应完成后，如果有工具调用但没有获取到工具名称，尝试从完整的响应中提取
        if (useFunctionCall && hasToolCalls && toolName.length() == 0 && !chatResponses.isEmpty()) {
            // 尝试从最后一个包含工具调用的响应中获取工具名称
            for (int i = chatResponses.size() - 1; i >= 0; i--) {
                ChatResponse response = chatResponses.get(i);
                Generation generation = response.getResult();
                if (generation != null) {
                    AssistantMessage assistantMessage = generation.getOutput();
                    if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                        String toolCallName = assistantMessage.getToolCalls().get(0).name();
                        if (StringUtils.hasText(toolCallName)) {
                            toolName.setLength(0);
                            toolName.append(toolCallName);
                            break;
                        }
                    }
                }
            }
            
            // 如果仍然没有获取到工具名称，尝试使用ToolCallingManager处理
            if (toolName.length() == 0) {
                try {
                    // 构建一个完整的ChatResponse用于工具调用处理
                    ChatResponse completeResponse = ChatResponse.builder()
                            .generations(chatResponses.stream()
                                    .map(ChatResponse::getResult)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList()))
                            .build();
                    
                    // 构建Prompt
                    UserMessage userMessage = new UserMessage(message);
                    Long userTimeMillis = session.getUserTimeMillis();
                    Conversation conversation = session.getConversation();
                    conversation.add(userMessage, userTimeMillis);
                    List<Message> messages = session.getConversation().messages();
                    ChatOptions chatOptions = ToolCallingChatOptions.builder()
                            .toolCallbacks(useFunctionCall && session.isSupportFunctionCall() ? session.getToolCallbacks() : new ArrayList<>())
                            .toolContext(TOOL_CONTEXT_SESSION_KEY, session)
                            .build();
                    Prompt prompt = new Prompt(messages, chatOptions);
                    
                    // 使用ToolCallingManager处理工具调用
                    ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, completeResponse);
                    
                    // 从工具执行结果中提取工具名称
                    List<Message> conversationHistory = toolExecutionResult.conversationHistory();
                    for (Message msg : conversationHistory) {
                        if (msg instanceof AssistantMessage) {
                            AssistantMessage assistantMsg = (AssistantMessage) msg;
                            if (!CollectionUtils.isEmpty(assistantMsg.getToolCalls())) {
                                String extractedToolName = assistantMsg.getToolCalls().get(0).name();
                                if (StringUtils.hasText(extractedToolName)) {
                                    toolName.setLength(0);
                                    toolName.append(extractedToolName);
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("尝试使用ToolCallingManager获取工具名称时出错: {}", e.getMessage(), e);
                }
            }
        }
        
        // 如果仍然没有获取到工具名称，尝试从会话的工具回调中推断
        if (toolName.length() == 0 && useFunctionCall && session.isSupportFunctionCall()) {
            List<ToolCallback> toolCallbacks = session.getToolCallbacks();
            if (!CollectionUtils.isEmpty(toolCallbacks)) {
                // 如果只有一个工具回调，使用它的名称
                if (toolCallbacks.size() == 1) {
                    String inferredToolName = toolCallbacks.get(0).getToolDefinition().name();
                    toolName.setLength(0);
                    toolName.append(inferredToolName);
                } else {
                    // 如果有多个工具回调，尝试从响应内容中推断
                    String responseText = fullResponse.toLowerCase();
                    for (ToolCallback callback : toolCallbacks) {
                        String callbackName = callback.getToolDefinition().name().toLowerCase();
                        if (responseText.contains(callbackName.replace("_", " ")) || 
                            responseText.contains(callbackName.replace("xiaoZhi_mcp_client_", ""))) {
                            toolName.setLength(0);
                            toolName.append(callback.getToolDefinition().name());
                            break;
                        }
                    }
                }
            }
        }
        
        // 如果仍然没有获取到工具名称，尝试从XiaoZhiToolCallingManager的记录中获取
        if (toolName.length() == 0 && useFunctionCall) {
            // 获取对话时间戳
            Long conversationTimestamp = session.getAssistantTimeMillis();
            if (conversationTimestamp == null) {
                conversationTimestamp = System.currentTimeMillis();
            }
            
            String recordedToolName = XiaoZhiToolCallingManager.getRecentToolCall(session.getSessionId(), conversationTimestamp);
            if (StringUtils.hasText(recordedToolName)) {
                toolName.setLength(0);
                toolName.append(recordedToolName);
            }
        }
        
        return toolName.toString();
    }

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
            if(useFunctionCall){
                //处理mcp自定义
                mcpSessionManager.customMcpHandler(session);
            }

            // 获取ChatModel
            ChatModel chatModel = chatModelFactory.takeChatModel(session);

            // 获取对话时间戳
            Long conversationTimestamp = session.getAssistantTimeMillis();
            if (conversationTimestamp == null) {
                conversationTimestamp = System.currentTimeMillis();
            }

            ChatOptions chatOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(useFunctionCall && session.isSupportFunctionCall() ? session.getToolCallbacks() : new ArrayList<>())
                    .toolContext(TOOL_CONTEXT_SESSION_KEY, session)
                    .toolContext("conversationTimestamp", conversationTimestamp)
                    .build();

            UserMessage userMessage = new UserMessage(message);
            Long userTimeMillis = session.getUserTimeMillis();
            Conversation conversation = session.getConversation();
            conversation.add(userMessage, userTimeMillis);
            List<Message> messages = conversation.messages();
            Prompt prompt = new Prompt(messages,chatOptions);

            ChatResponse chatResponse = chatModel.call(prompt);
            if (chatResponse == null || chatResponse.getResult().getOutput().getText() == null) {
                logger.warn("模型响应为空或无生成内容");
                return "抱歉，我在处理您的请求时遇到了问题。请稍后再试。";
            }
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();

            // 检查是否有工具调用，如果有则处理工具调用
            if (useFunctionCall && !CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                // 使用工具调用管理器处理工具调用
                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
                
                // 获取工具调用后的最终响应
                List<Message> conversationHistory = toolExecutionResult.conversationHistory();
                if (!conversationHistory.isEmpty()) {
                    Message lastMessage = conversationHistory.get(conversationHistory.size() - 1);
                    if (lastMessage instanceof AssistantMessage) {
                        assistantMessage = (AssistantMessage) lastMessage;
                    }
                }
                
                // 如果工具调用返回直接结果，则直接返回
                if (toolExecutionResult.returnDirect()) {
                    return assistantMessage.getText();
                }
            }

            final AssistantMessage finalAssistantMessage = assistantMessage;
            Thread.startVirtualThread(() -> {// 异步持久化
                // 保存AI消息，会被持久化至数据库。
                session.getConversation().add(finalAssistantMessage,session.getAssistantTimeMillis());
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
        if(useFunctionCall){
            //处理mcp自定义
            mcpSessionManager.customMcpHandler(session);
        }

        // 获取ChatModel
        ChatModel chatModel = chatModelFactory.takeChatModel(session);

        // 获取对话时间戳
        Long conversationTimestamp = session.getAssistantTimeMillis();
        if (conversationTimestamp == null) {
            conversationTimestamp = System.currentTimeMillis();
        }

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(useFunctionCall && session.isSupportFunctionCall() ? session.getToolCallbacks() : new ArrayList<>())
                .toolContext(TOOL_CONTEXT_SESSION_KEY, session)
                .toolContext("conversationTimestamp", conversationTimestamp)
                .build();

        UserMessage userMessage = new UserMessage(message);
        Long userTimeMillis = session.getUserTimeMillis();
        Conversation conversation = session.getConversation();
        conversation.add(userMessage, userTimeMillis);
        List<Message> messages = conversation.messages();
        Prompt prompt = new Prompt(messages, chatOptions);

        // 调用实际的流式聊天方法
        Flux<ChatResponse> chatResponseFlux = chatModel.stream(prompt);
        logger.info("end chat stream");
        return chatResponseFlux;
    }


    public void chatStreamBySentence(ChatSession session, String message, boolean useFunctionCall,
                                     TriConsumer<String, Boolean, Boolean> sentenceHandler) {
        try {
            // 在对话开始时清除工具调用记录，确保每次对话都是干净的
            XiaoZhiToolCallingManager.clearRecentToolCall(session.getSessionId());
            
            // 创建流式响应监听器
            StreamResponseListener streamListener = new TokenStreamResponseListener(session, message, sentenceHandler, useFunctionCall);
            final StringBuilder toolName = new StringBuilder(); // 当前句子的缓冲区
            final StringBuilder fullResponse = new StringBuilder(); // 完整响应的缓冲区
            final List<ChatResponse> chatResponses = new ArrayList<>(); // 收集所有的ChatResponse
            final AtomicBoolean hasToolCalls = new AtomicBoolean(false); // 标记是否有工具调用
            
            AtomicReference<Usage> llmUsage = new AtomicReference<>();
            // 调用现有的流式方法
            chatStream(session, message, useFunctionCall)
                    .subscribe(
                            chatResponse -> {
                                // 收集所有的ChatResponse用于后续处理
                                chatResponses.add(chatResponse);
                                
                                String token = chatResponse.getResult() == null
                                        || chatResponse.getResult().getOutput() == null
                                        || chatResponse.getResult().getOutput().getText() == null ? ""
                                                : chatResponse.getResult().getOutput().getText();
                                if (!token.isEmpty()) {
                                    fullResponse.append(token);
                                    streamListener.onToken(token);
                                }
                                
                                // 检查是否有工具调用
                                if (useFunctionCall) {
                                    Generation generation = chatResponse.getResult();
                                    if (generation != null) {
                                        // 检查AssistantMessage是否有工具调用
                                        AssistantMessage assistantMessage = generation.getOutput();
                                        if (assistantMessage.hasToolCalls() || !CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                                            hasToolCalls.set(true);
                                            if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                                                String toolCallName = assistantMessage.getToolCalls().get(0).name();
                                                if (StringUtils.hasText(toolCallName)) {
                                                    toolName.setLength(0);
                                                    toolName.append(toolCallName);
                                                }
                                            }
                                        }
                                    }
                                }
                                if(chatResponse.getMetadata().getUsage() != null && chatResponse.getMetadata().getUsage().getTotalTokens() > 0) {
                                    llmUsage.set(chatResponse.getMetadata().getUsage());
                                }
                            },
                            streamListener::onError,
                            () -> {
                                // 使用提取的方法获取工具名称
                                String extractedToolName = getToolName(session, message, useFunctionCall, 
                                        chatResponses, fullResponse.toString(), hasToolCalls.get());
                                if (StringUtils.hasText(extractedToolName)) {
                                    toolName.setLength(0);
                                    toolName.append(extractedToolName);
                                }
                                
                                streamListener.onComplete(toolName.toString(), llmUsage.get());
                            });
        } catch (Exception e) {
            logger.error("处理LLM流式请求时发生异常", e);
            sentenceHandler.accept("抱歉，处理您的请求时遇到问题，请稍后再试。", true, true);
        }
    }


    /**
     * 判断文本是否包含实质性内容（不仅仅是空白字符或标点符号）
     *
     * @param text 要检查的文本
     * @return 是否包含实质性内容
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
        String token = chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null
                || chatResponse.getResult().getOutput().getText() == null ? ""
                : chatResponse.getResult().getOutput().getText();
        return token;
    }

    class TokenStreamResponseListener implements StreamResponseListener {

        final StringBuilder currentSentence = new StringBuilder(); // 当前句子的缓冲区
        final StringBuilder contextBuffer = new StringBuilder(); // 上下文缓冲区，用于检测数字中的小数点
        final AtomicInteger sentenceCount = new AtomicInteger(0); // 已发送句子的计数
        final StringBuilder fullResponse = new StringBuilder(); // 完整响应的缓冲区
        final AtomicBoolean finalSentenceSent = new AtomicBoolean(false); // 跟踪最后一个句子是否已发送
        String message;// 用户消息内容
        ChatSession session;
        TriConsumer<String, Boolean, Boolean> sentenceHandler;
        boolean useFunctionCall;

        public TokenStreamResponseListener(ChatSession session, String message,
                TriConsumer<String, Boolean, Boolean> sentenceHandler, boolean useFunctionCall) {
            this.message = message;
            this.session = session;
            this.sentenceHandler = sentenceHandler;
            this.useFunctionCall = useFunctionCall;
        }

        ChatGenerationMetadata metadata = generation.getMetadata();
        if (metadata == null) {
            logger.debug("响应元数据为空，无法提取工具名称");
            return;
        }

        @Override
        public void onComplete(String toolName, Usage llmUsage) {
            // 检查该会话是否已完成处理
            // 处理当前缓冲区剩余的内容（如果有）
            if (currentSentence.length() > 0 && !finalSentenceSent.get()) {
                String sentence = currentSentence.toString().trim();
                boolean isFirst = sentenceCount.get() == 0;
                boolean isLast = true; // 这是最后一个句子

                // 即使句子很短，也要发送（比如"再见"这样的词）
                sentenceHandler.accept(sentence, isFirst, isLast);
                sentenceCount.incrementAndGet();
                finalSentenceSent.set(true);
            } else if (!finalSentenceSent.get() && sentenceCount.get() > 0) {
                // 如果已经发送过句子但没有发送过最后一个句子标记，发送一个空的最后句子标记
                // 这确保对话能够正常结束
                boolean isFirst = false; // 不是第一句
                sentenceHandler.accept("", isFirst, true);
                finalSentenceSent.set(true);
            }

            persistMessages(toolName, llmUsage);

            // 记录处理的句子数量
            logger.debug("总共处理了 {} 个句子", sentenceCount.get());
        }
    }

        /**
         * 保存消息,只保存用户输入与输出。
         * Message在没有持久化前，是不会有messageId的。
         * 是否需要把content为空和角色为tool的入库?
         * 目前不入库（这类主要是function_call的二次调用llm进行总结时的过程消息）
         * 具体的细节逻辑，由Conversation处理，ChatService不再负责消息持久化的职能。
         */
        void persistMessages(String toolName, Usage llmUsage) {
            
            UserMessage userMessage = new UserMessage(message);
            Long userTimeMillis = session.getUserTimeMillis();

            AssistantMessage assistantMessage = new AssistantMessage(fullResponse.toString(), Map.of("toolName", toolName));
            // 将Usage附加于AssistantMessage的metadata。以后可以考虑封装AssistantMessage的子类，减少metadata样板代码。
            ChatMemory.setUsage(assistantMessage, llmUsage);
            // 首次模型响应时间、首次TTS响应时间都是AssistantMessage才具备的metadata，UserMessage没有实际也不应该有。
            // Message里塞的内容已经较多，后续再考虑优化。可能设计一个新的类，融合 SysMessage and Message是值得考虑的。
            assistantMessage.getMetadata().put(ChatSession.ATTR_FIRST_MODEL_RESPONSE_TIME,
                    session.getAttributes().get(ChatSession.ATTR_FIRST_MODEL_RESPONSE_TIME));
            
            // 获取TTS响应时间，如果还没有设置（流式TTS可能还在处理中），等待一下再获取
            Object ttsResponseTime = session.getAttributes().get(ChatSession.ATTR_FIRST_TTS_RESPONSE_TIME);
            if (ttsResponseTime == null) {
                // 如果TTS响应时间还没有设置，等待更长时间再获取
                try {
                    Thread.sleep(1500); // 等待1秒，让流式TTS有机会设置响应时间
                    ttsResponseTime = session.getAttributes().get(ChatSession.ATTR_FIRST_TTS_RESPONSE_TIME);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            assistantMessage.getMetadata().put(ChatSession.ATTR_FIRST_TTS_RESPONSE_TIME, ttsResponseTime);
            
            Long assistantTimeMillis = session.getAssistantTimeMillis();
            session.getConversation().add(assistantMessage, assistantTimeMillis);
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
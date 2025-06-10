package com.xiaozhi.dialogue.llm;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.api.StreamResponseListener;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.dialogue.llm.memory.ChatMemory;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.utils.EmojiUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * 负责管理和协调LLM相关功能
 * TODO 重构：改成Domain Entity: ChatRole(聊天角色)，管理对话历史记录，管理对话工具调用等。
 */
@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    public static final String TOOL_CONTEXT_SESSION_KEY = "session";

    // 句子结束标点符号模式（中英文句号、感叹号、问号）
    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile("[。！？!?]");

    // 逗号、分号等停顿标点
    private static final Pattern PAUSE_PATTERN = Pattern.compile("[，、；,;]");

    // 冒号和引号等特殊标点
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[：:\"]");

    // 换行符
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("[\n\r]");

    // 数字模式（用于检测小数点是否在数字中）
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+\\.\\d+");

    // 表情符号模式
    private static final Pattern EMOJI_PATTERN = Pattern.compile("\\p{So}|\\p{Sk}|\\p{Sm}");

    // 最小句子长度（字符数）
    private static final int MIN_SENTENCE_LENGTH = 5;

    // 新句子判断的字符阈值
    private static final int NEW_SENTENCE_TOKEN_THRESHOLD = 8;

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
        return chatModel.stream(prompt);
    }

    public void chatStreamBySentence(ChatSession session, String message, boolean useFunctionCall,
            TriConsumer<String, Boolean, Boolean> sentenceHandler) {
        try {
            // 创建流式响应监听器
            StreamResponseListener streamListener = new TokenStreamResponseListener(session, message, sentenceHandler);
            final StringBuilder toolName = new StringBuilder(); // 当前句子的缓冲区
            // 调用现有的流式方法
            chatStream(session, message, useFunctionCall)
                    .subscribe(
                            chatResponse -> {
                                String token = chatResponse.getResult() == null
                                        || chatResponse.getResult().getOutput() == null
                                        || chatResponse.getResult().getOutput().getText() == null ? ""
                                                : chatResponse.getResult().getOutput().getText();
                                if (!token.isEmpty()) {
                                    streamListener.onToken(token);
                                }
                                logger.info("toolName = {},useFunctionCall={}", toolName, useFunctionCall);
                                if (toolName.isEmpty() && useFunctionCall) {
                                    Generation generation = chatResponse.getResult();
                                    // 注意，不能用chatResponse.hasToolCalls()判断，当前chatResponse工具调用结果的返回，
                                    // 是个文本类助手消息，hasToolCalls标识是false。必须溯源取meta
                                    if (generation != null) {
                                        ChatGenerationMetadata chatGenerationMetadata = generation.getMetadata();
                                        String name = chatGenerationMetadata.get("toolName");
                                        if (name != null && !name.isEmpty()) {
                                            toolName.append(name);
                                        }
                                    }
                                }
                            },
                            streamListener::onError,
                            () -> streamListener.onComplete(toolName.toString()));
        } catch (Exception e) {
            logger.error("处理LLM时出错: {}", e.getMessage(), e);
            // 发送错误信号
            sentenceHandler.accept("抱歉，我在处理您的请求时遇到了问题。", true, true);
        }
    }


    /**
     * 清除设备缓存
     * 
     * @param deviceId 设备ID
     */
    public void clearMessageCache(String deviceId) {
        chatMemoryStore.clearMessages(deviceId);
    }

    /**
     * 判断文本是否包含实质性内容（不仅仅是空白字符或标点符号）
     *
     * @param text 要检查的文本
     * @return 是否包含实质性内容
     */
    private boolean containsSubstantialContent(String text) {
        if (text == null || text.trim().length() < MIN_SENTENCE_LENGTH) {
            return false;
        }

        // 移除所有标点符号和空白字符后，检查是否还有内容
        String stripped = text.replaceAll("[\\p{P}\\s]", "");
        return stripped.length() >= 2; // 至少有两个非标点非空白字符
    }

    /**
     * 三参数消费者接口
     */
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
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

        public TokenStreamResponseListener(ChatSession session, String message,
                TriConsumer<String, Boolean, Boolean> sentenceHandler) {
            this.message = message;
            this.session = session;
            this.sentenceHandler = sentenceHandler;
        }

        @Override
        public void onToken(String token) {
            if (token == null || token.isEmpty()) {
                return;
            }
            // 将token添加到完整响应
            fullResponse.append(token);

            // 逐字符处理token
            for (int i = 0; i < token.length();) {
                int codePoint = token.codePointAt(i);
                String charStr = new String(Character.toChars(codePoint));

                // 将字符添加到上下文缓冲区（保留最近的字符以检测数字模式）
                contextBuffer.append(charStr);
                if (contextBuffer.length() > 20) { // 保留足够的上下文
                    contextBuffer.delete(0, contextBuffer.length() - 20);
                }

                // 将字符添加到当前句子缓冲区
                currentSentence.append(charStr);

                // 检查各种断句标记
                boolean shouldSendSentence = false;
                boolean isEndMark = SENTENCE_END_PATTERN.matcher(charStr).find();
                boolean isPauseMark = PAUSE_PATTERN.matcher(charStr).find();
                boolean isSpecialMark = SPECIAL_PATTERN.matcher(charStr).find();
                boolean isNewline = NEWLINE_PATTERN.matcher(charStr).find();
                boolean isEmoji = EmojiUtils.isEmoji(codePoint);

                // 检查当前句子是否包含颜文字
                boolean containsKaomoji = false;
                if (currentSentence.length() >= 3) { // 颜文字至少需要3个字符
                    containsKaomoji = EmojiUtils.containsKaomoji(currentSentence.toString());
                }

                // 如果当前字符是句号，检查它是否是数字中的小数点
                if (isEndMark && charStr.equals(".")) {
                    String context = contextBuffer.toString();
                    Matcher numberMatcher = NUMBER_PATTERN.matcher(context);
                    // 如果找到数字模式（如"0.271"），则不视为句子结束标点
                    if (numberMatcher.find() && numberMatcher.end() >= context.length() - 3) {
                        isEndMark = false;
                    }
                }

                // 判断是否应该发送当前句子
                if (isEndMark) {
                    // 句子结束标点是强断句信号
                    shouldSendSentence = true;
                } else if (isNewline) {
                    // 换行符也是强断句信号
                    shouldSendSentence = true;
                } else if ((isPauseMark || isSpecialMark || isEmoji || containsKaomoji)
                        && currentSentence.length() >= MIN_SENTENCE_LENGTH) {
                    // 停顿标点、特殊标点、表情符号或颜文字在句子足够长时可以断句
                    shouldSendSentence = true;
                }

                // 如果应该发送句子，且当前句子长度满足要求
                if (shouldSendSentence && currentSentence.length() >= MIN_SENTENCE_LENGTH) {
                    String sentence = currentSentence.toString().trim();

                    // 过滤颜文字
                    sentence = EmojiUtils.filterKaomoji(sentence);

                    if (containsSubstantialContent(sentence)) {
                        boolean isFirst = sentenceCount.get() == 0;
                        boolean isLast = false; // 只有在onComplete中才会有最后一个句子

                        sentenceHandler.accept(sentence, isFirst, isLast);
                        sentenceCount.incrementAndGet();

                        // 清空当前句子缓冲区
                        currentSentence.setLength(0);
                    }
                }

                // 移动到下一个码点
                i += Character.charCount(codePoint);
            }
        }

        @Override
        public void onComplete(String toolName) {
            // 检查该会话是否已完成处理
            // 处理当前缓冲区剩余的内容（如果有）
            if (currentSentence.length() > 0 && containsSubstantialContent(currentSentence.toString())
                    && !finalSentenceSent.get()) {
                String sentence = currentSentence.toString().trim();
                boolean isFirst = sentenceCount.get() == 0;
                boolean isLast = true; // 这是最后一个句子

                sentenceHandler.accept(sentence, isFirst, isLast);
                sentenceCount.incrementAndGet();
                finalSentenceSent.set(true);
            } else if (!finalSentenceSent.get()) {
                // 如果没有剩余内容但也没有发送过最后一个句子，发送一个空的最后句子标记
                // 这确保即使没有剩余内容，也会发送最后一个句子标记
                boolean isFirst = sentenceCount.get() == 0;
                sentenceHandler.accept("", isFirst, true);
                finalSentenceSent.set(true);
            }

            persistMessages(toolName);

            // 记录处理的句子数量
            logger.debug("总共处理了 {} 个句子", sentenceCount.get());
        }

        /**
         * 保存消息,只保存用户输入与输出。
         * Message在没有持久化前，是不会有messageId的。
         * 是否需要把content为空和角色为tool的入库?
         * 目前不入库（这类主要是function_call的二次调用llm进行总结时的过程消息）
         * 具体的细节逻辑，由Conversation处理，ChatService不再负责消息持久化的职能。
         */
        void persistMessages(String toolName) {
            UserMessage userMessage = new UserMessage(message);
            Long userTimeMillis = session.getUserTimeMillis();
            AssistantMessage assistantMessage = new AssistantMessage(fullResponse.toString(), Map.of("toolName", toolName));
            Long assistantTimeMillis = session.getAssistantTimeMillis();
            session.getConversation().addMessage(userMessage, userTimeMillis, assistantMessage, assistantTimeMillis);
        }

        @Override
        public void onError(Throwable e) {
            logger.error("流式响应出错: {}", e.getMessage(), e);
            // 发送错误信号
            sentenceHandler.accept("抱歉，我在处理您的请求时遇到了问题。", true, true);

        }
    };
}
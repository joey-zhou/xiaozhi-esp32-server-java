package com.xiaozhi.dialogue.llm.api;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.utils.EmojiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

public class TokenStreamResponseListener implements StreamResponseListener {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    // 常量定义（可根据需求外部配置）
    private static final int CONTEXT_BUFFER_MAX_LENGTH = 20;
    private static final int MIN_SENTENCE_LENGTH = 5; // 示例值，可调整
    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile("[。？！.!?]");
    private static final Pattern PAUSE_PATTERN = Pattern.compile("[，、；,;]");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[：:“”‘’\"'()（）]");
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("[\n\r]");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+\\.\\d+");

    // 缓冲区与状态管理
    private final StringBuilder currentSentence = new StringBuilder();
    private final StringBuilder contextBuffer = new StringBuilder();
    private final AtomicInteger sentenceCount = new AtomicInteger(0);
    private final StringBuilder fullResponse = new StringBuilder();
    private final AtomicBoolean finalSentenceSent = new AtomicBoolean(false);

    // 依赖注入
    private final String userMessage;
    private final ChatSession session;

    TriConsumer<String, Boolean, Boolean> sentenceHandler;

    public TokenStreamResponseListener(ChatSession session, String userMessage,
                                       TriConsumer<String, Boolean, Boolean> sentenceHandler) {
        this.session = session;
        this.userMessage = userMessage;
        this.sentenceHandler = sentenceHandler;
    }

    @Override
    public void onToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        fullResponse.append(token);
        processTokenCharacters(token);
    }

    /**
     * 处理token中的每个字符，提取句子并触发回调
     */
    private void processTokenCharacters(String token) {
        int length = token.length();
        for (int i = 0; i < length; ) {
            int codePoint = token.codePointAt(i);
            // 过滤控制字符
            if (Character.isISOControl(codePoint) && !isNewline(codePoint)) {
                i += Character.charCount(codePoint);
                continue;
            }

            updateBuffers(codePoint);
            boolean shouldSend = checkShouldSendSentence(codePoint);

            if (shouldSend && currentSentence.length() >= MIN_SENTENCE_LENGTH) {
                sendCurrentSentence(false);
            }

            i += Character.charCount(codePoint);
        }
    }

    /**
     * 更新上下文缓冲区和当前句子缓冲区
     */
    private void updateBuffers(int codePoint) {
        char[] chars = Character.toChars(codePoint);
        // 更新上下文缓冲区（保留最近的字符）
        contextBuffer.append(chars);
        if (contextBuffer.length() > CONTEXT_BUFFER_MAX_LENGTH) {
            contextBuffer.delete(0, contextBuffer.length() - CONTEXT_BUFFER_MAX_LENGTH);
        }
        // 更新当前句子缓冲区
        currentSentence.append(chars);
    }

    /**
     * 判断是否应该发送当前句子
     */
    private boolean checkShouldSendSentence(int codePoint) {
        String charStr = new String(Character.toChars(codePoint));

        // 基础断句标记判断
        boolean isEndMark = isSentenceEnd(charStr, codePoint);
        boolean isNewline = isNewline(codePoint);
        boolean isPauseMark = PAUSE_PATTERN.matcher(charStr).matches();
        boolean isSpecialMark = SPECIAL_PATTERN.matcher(charStr).matches();
        boolean isEmoji = EmojiUtils.isEmoji(codePoint);
        boolean containsKaomoji = currentSentence.length() >= 3
                && EmojiUtils.containsKaomoji(currentSentence.toString());

        // 断句逻辑判断
        if (isEndMark || isNewline) {
            return true;
        }
        if ((isPauseMark || isSpecialMark || isEmoji || containsKaomoji)
                && currentSentence.length() >= MIN_SENTENCE_LENGTH) {
            return true;
        }
        return false;
    }

    /**
     * 判断是否为句子结束标记（排除数字中的小数点）
     */
    private boolean isSentenceEnd(String charStr, int codePoint) {
        if (!SENTENCE_END_PATTERN.matcher(charStr).matches()) {
            return false;
        }
        // 处理小数点特殊情况
        if (codePoint == '.') {
            String context = contextBuffer.toString();
            Matcher numberMatcher = NUMBER_PATTERN.matcher(context);
            if (numberMatcher.find() && numberMatcher.end() >= context.length() - 3) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是否为换行符
     */
    private boolean isNewline(int codePoint) {
        return codePoint == '\n' || codePoint == '\r';
    }

    /**
     * 发送当前句子并重置缓冲区
     */
    private void sendCurrentSentence(boolean isLast) {
        String sentence = currentSentence.toString().trim();
        sentence = EmojiUtils.filterKaomoji(sentence);

        if (containsSubstantialContent(sentence)) {
            boolean isFirst = sentenceCount.get() == 0;
            sentenceHandler.accept(sentence, isFirst, isLast);
            sentenceCount.incrementAndGet();
        }
        currentSentence.setLength(0);
    }

    @Override
    public void onComplete(String toolName) {
        try {
            // 处理剩余内容
            if (currentSentence.length() > 0 && containsSubstantialContent(currentSentence.toString())
                    && !finalSentenceSent.get()) {
                sendCurrentSentence(true);
                finalSentenceSent.set(true);
            } else if (!finalSentenceSent.get()) {
                // 确保发送结束标记
                boolean isFirst = sentenceCount.get() == 0;
                sentenceHandler.accept("", isFirst, true);
                finalSentenceSent.set(true);
            }
            persistMessages(toolName);
            logger.debug("会话处理完成，共发送 {} 个句子", sentenceCount.get());
        } finally {
            // 清理缓冲区，避免内存泄漏
            clearBuffers();
        }
    }

    /**
     * 持久化消息（过滤空内容）
     */
    private void persistMessages(String toolName) {
        if (session.getConversation() == null) {
            logger.warn("会话Conversation为空，跳过消息持久化");
            return;
        }
        String responseContent = fullResponse.toString().trim();
        if (responseContent.isEmpty()) {
            logger.debug("响应内容为空，跳过持久化");
            return;
        }

        UserMessage userMsg = new UserMessage(userMessage);
        AssistantMessage assistantMsg = new AssistantMessage(responseContent,
                Map.of("toolName", toolName == null ? "default" : toolName));

        session.getConversation().addMessage(
                userMsg,
                session.getUserTimeMillis(),
                assistantMsg,
                session.getAssistantTimeMillis()
        );
    }


    @Override
    public void onError(Throwable e) {
        logger.error("流式处理错误（会话ID: {}）", session.getSessionId(), e);
        String errorMsg = e instanceof IOException ? "网络异常，请重试" : "抱歉，我在处理您的请求时遇到了问题。";
        sentenceHandler.accept(errorMsg, true, true);
        clearBuffers();
    }
    /**
     * 检查内容是否包含实质信息
     */
    private boolean containsSubstantialContent(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        // 过滤仅包含空白字符或标点的内容
        return content.replaceAll("[\\s\\p{P}]", "").length() > 0;
    }

    /**
     * 清理缓冲区
     */
    private void clearBuffers() {
        currentSentence.setLength(0);
        contextBuffer.setLength(0);
        fullResponse.setLength(0);
    }
}

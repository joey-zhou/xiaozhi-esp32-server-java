package com.xiaozhi.ai.tts;

import com.xiaozhi.utils.EmojiUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 句子处理帮助类，统一分句逻辑。
 * 有状态的实例，不要复用，用完就丢弃。
 *
 * 提供两种使用方式：
 * 1. 响应式：convert(Flux<String>) → Flux<String>，供 FileSynthesizer 使用
 * 2. 命令式：take(String token) / take()，供 TTS Provider 内部 WebSocket 订阅使用
 */
public class SentenceHelper implements ChatConverter {

    /**
     * 分句结果，包含去除表情符号后的纯文本和提取的情绪词。
     */
    public record SentenceResult(String text, String mood) {}
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

    // 最小句子长度（字符数）
    private static final int MIN_SENTENCE_LENGTH = 8;

    // 上下文缓冲区最大长度（用于数字小数点检测等上下文判断）
    private static final int CONTEXT_BUFFER_MAX_LENGTH = 20;

    private final StringBuilder currentSentence = new StringBuilder();
    private final StringBuilder contextBuffer = new StringBuilder();

    public SentenceHelper() {
    }

    /**
     * 命令式分句：逐 token 输入，返回检测到的完整句子，未成句则返回空字符串。
     * 供 TTS Provider 内部 WebSocket 订阅回调使用。
     */
    public List<SentenceResult> take(String token) {
        List<SentenceResult> sentences = new ArrayList<>();
        if (token == null || token.isEmpty()) {
            return sentences;
        }

        for (int i = 0; i < token.length();) {
            int codePoint = token.codePointAt(i);
            String charStr = new String(Character.toChars(codePoint));

            contextBuffer.append(charStr);
            if (contextBuffer.length() > CONTEXT_BUFFER_MAX_LENGTH) {
                contextBuffer.delete(0, contextBuffer.length() - CONTEXT_BUFFER_MAX_LENGTH);
            }

            currentSentence.append(charStr);

            boolean isEndMark = SENTENCE_END_PATTERN.matcher(charStr).find();
            boolean isPauseMark = PAUSE_PATTERN.matcher(charStr).find();
            boolean isSpecialMark = SPECIAL_PATTERN.matcher(charStr).find();
            boolean isNewline = NEWLINE_PATTERN.matcher(charStr).find();
            boolean isEmoji = EmojiUtils.isEmoji(codePoint);

            boolean containsKaomoji = false;
            if (currentSentence.length() >= 3) {
                containsKaomoji = EmojiUtils.containsKaomoji(currentSentence.toString());
            }

            if (isEndMark && charStr.equals(".")) {
                String context = contextBuffer.toString();
                Matcher numberMatcher = NUMBER_PATTERN.matcher(context);
                if (numberMatcher.find() && numberMatcher.end() >= context.length() - 3) {
                    isEndMark = false;
                }
            }

            boolean shouldSendSentence = false;
            if (isEndMark || isNewline) {
                shouldSendSentence = true;
            } else if ((isPauseMark || isSpecialMark || isEmoji || containsKaomoji)
                    && currentSentence.length() >= MIN_SENTENCE_LENGTH) {
                shouldSendSentence = true;
            }

            if (shouldSendSentence && currentSentence.length() >= MIN_SENTENCE_LENGTH) {
                String rawSentence = currentSentence.toString().trim();
                List<String> moods = new ArrayList<>();
                String cleanSentence = EmojiUtils.processSentence(rawSentence, moods);
                if (containsSubstantialContent(cleanSentence)) {
                    String mood = moods.isEmpty() ? null : moods.get(0);
                    sentences.add(new SentenceResult(cleanSentence, mood));
                    currentSentence.setLength(0);
                }
            }

            i += Character.charCount(codePoint);
        }

        return sentences;
    }

    /**
     * 命令式分句：刷出缓冲区剩余内容（在文本流结束时调用）。
     */
    public SentenceResult take() {
        String rawSentence = currentSentence.toString().trim();
        if (rawSentence.isEmpty()) {
            return new SentenceResult("", null);
        }
        List<String> moods = new ArrayList<>();
        String cleanSentence = EmojiUtils.processSentence(rawSentence, moods);
        String mood = moods.isEmpty() ? null : moods.get(0);
        return new SentenceResult(cleanSentence, mood);
    }

    public void onToken(String token, FluxSink<SentenceResult> sink) {
        for (SentenceResult result : take(token)) {
            sink.next(result);
        }
    }

    public void onComplete(FluxSink<SentenceResult> sink) {
        SentenceResult result = take();
        if (StringUtils.hasText(result.text())) {
            sink.next(result);
        }
        sink.complete();
    }

    public Flux<SentenceResult> convert(Flux<String> stringFlux) {
        return Flux.create(sink ->
                stringFlux.subscribe(
                        token -> this.onToken(token, sink),
                        sink::error,
                        () -> this.onComplete(sink)));
    }

    private boolean containsSubstantialContent(String text) {
        if (text == null || text.trim().length() < MIN_SENTENCE_LENGTH) {
            return false;
        }
        String stripped = text.replaceAll("[\\p{P}\\s]", "");
        return stripped.length() >= 2;
    }
}

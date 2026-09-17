package com.xiaozhi.ai.tts;

/**
 * 分句结果，包含去除表情符号后的纯文本和提取的情绪词。
 */
public record SentenceResult(String text, String mood) {}

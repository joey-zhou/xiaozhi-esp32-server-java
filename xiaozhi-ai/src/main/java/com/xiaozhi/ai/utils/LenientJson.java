package com.xiaozhi.ai.utils;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 把模型吐出来的「差不多是 JSON」整理成能解析的 JSON 对象文本。
 * 小模型经常少写最后一个右括号、把 JSON 包在代码围栏里、或在前后夹一句解释，
 * 这些都不值得让一整批记忆抽取失败。
 * <p>
 * 结构补齐归 {@link #object}，语法走样（收尾多逗号、单引号、字段名不带引号、字符串里裸换行、注释）
 * 归 {@link #mapper()} 放宽的解析器，两者配合使用。
 */
public final class LenientJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private LenientJson() {
    }

    /** 解析模型输出用的宽松 Jackson：接受多余逗号、单引号、裸字段名、裸控制字符和注释，忽略多出来的字段 */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * 取出文本里的 JSON 对象：去掉代码围栏与前后说明，缺的右括号补齐。
     * 找不到左花括号时原样返回，交给后面的解析去报错。
     */
    public static String object(String raw) {
        if (raw == null) {
            return null;
        }
        String text = stripFences(raw).trim();
        int start = text.indexOf('{');
        if (start < 0) {
            return text;
        }
        text = text.substring(start);
        String completed = complete(text);
        if (!completed.equals(text)) {
            return completed;
        }
        int end = text.lastIndexOf('}');
        return end >= 0 ? text.substring(0, end + 1) : text;
    }

    /**
     * 补齐没闭合的字符串、数组与对象；去掉截断处悬空的逗号。已经闭合的文本原样返回。
     */
    public static String complete(String text) {
        if (text == null) {
            return null;
        }
        Deque<Character> closers = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> closers.push('}');
                case '[' -> closers.push(']');
                case '}', ']' -> {
                    if (!closers.isEmpty() && closers.peek() == c) {
                        closers.pop();
                    }
                }
                default -> {
                }
            }
        }
        if (closers.isEmpty() && !inString) {
            return text;
        }
        StringBuilder repaired = new StringBuilder(text);
        if (inString) {
            repaired.append('"');
        }
        int last = repaired.length() - 1;
        while (last >= 0 && Character.isWhitespace(repaired.charAt(last))) {
            last--;
        }
        if (last >= 0 && repaired.charAt(last) == ',') {
            repaired.setLength(last);
        }
        while (!closers.isEmpty()) {
            repaired.append(closers.pop());
        }
        return repaired.toString();
    }

    private static String stripFences(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        String body = firstLineEnd < 0 ? "" : trimmed.substring(firstLineEnd + 1);
        int closing = body.lastIndexOf("```");
        return closing >= 0 ? body.substring(0, closing) : body;
    }
}

package com.xiaozhi.ai.stt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语音识别热词。
 * <p>
 * 配置形态是每行一个「词」或「词 权重」的纯文本，与 provider 无关；各 provider 自行映射成
 * 自家格式（腾讯 {@code 词|权重} 逗号分隔、FunASR {@code {"词":权重}}），不支持热词的 provider 忽略。
 */
public record Hotword(String text, int weight) {

    /** 不写权重时用的值，取腾讯通用热词区间(1-10)的上限 */
    public static final int DEFAULT_WEIGHT = 10;

    /** 权重下限与上限，按最严的 provider（腾讯：1-11 为普通与超级热词）收口 */
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 11;

    /** 单个热词的最大字符数 */
    public static final int MAX_TEXT_LENGTH = 30;

    /** 解析后保留的最大条数，超出部分丢弃 */
    public static final int MAX_COUNT = 100;

    /**
     * 解析配置文本。非法行（空行、超长、权重不是整数）跳过而不是整份作废，重复的词以先出现的为准。
     * 传给具体 provider 前还要按各家上限再截断一次。
     */
    public static List<Hotword> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Map<String, Hotword> byText = new LinkedHashMap<>();
        for (String line : raw.split("\\R")) {
            Hotword hotword = parseLine(line);
            if (hotword != null && byText.size() < MAX_COUNT) {
                byText.putIfAbsent(hotword.text(), hotword);
            }
        }
        return List.copyOf(byText.values());
    }

    /** 权重与词之间用最后一个空白分隔，词本身允许含空格（"Model Y"、"AirPods Pro"） */
    private static Hotword parseLine(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int split = trimmed.lastIndexOf(' ');
        if (split > 0) {
            Integer weight = parseWeight(trimmed.substring(split + 1));
            if (weight != null) {
                String text = trimmed.substring(0, split).trim();
                return text.isEmpty() || text.length() > MAX_TEXT_LENGTH ? null : new Hotword(text, weight);
            }
        }
        return trimmed.length() > MAX_TEXT_LENGTH ? null : new Hotword(trimmed, DEFAULT_WEIGHT);
    }

    private static Integer parseWeight(String token) {
        try {
            int weight = Integer.parseInt(token);
            return weight < MIN_WEIGHT || weight > MAX_WEIGHT ? null : weight;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 各 provider 按自家上限截断，超出的丢掉而不是整份不发 */
    public static List<Hotword> limit(List<Hotword> hotwords, int max) {
        if (hotwords == null || hotwords.isEmpty()) {
            return List.of();
        }
        return hotwords.size() <= max ? hotwords : new ArrayList<>(hotwords.subList(0, max));
    }
}

package com.xiaozhi.ai.llm.intent;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 退出关键词检测器
 * 用于检测用户输入中是否包含明确的退出意图关键词
 * 注意：这个类不再是 Spring 组件，而是作为工具类被 IntentDetector 使用
 */
public class ExitKeywordDetector {

    /**
     * 退出关键词列表
     * 包含各种表达退出、结束对话的词汇
     */
    private static final List<String> EXIT_KEYWORDS = Arrays.asList(
            "拜拜",
            "再见",
            "退下",
            "走了",
            "我走了",
            "我要走了",
            "结束对话",
            "退出",
            "下线",
            "结束",
            "告辞",
            "告退",
            "离开",
            "goodbye",
            "bye",
            "bye bye",
            "byebye",
            "see you",
            "see ya"
    );

    /**
     * 精确匹配的短语模式
     * 匹配包含退出意图关键词的输入
     */
    private static final Pattern EXIT_PATTERN = Pattern.compile(
            ".*(?:拜拜|再见|退下|结束对话|退出|告辞|告退"
            + "|(?:我|你)?(?:先)?(?:要)?(?:走了|离开|下线)"
            + "|bye\\s*bye|goodbye|see\\s+(?:you|ya)).*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 排除的短语模式
     * 包含这些短语时不应该触发退出
     * 例如："不要退出"、"别走"、"不离开" 等
     */
    private static final Pattern EXCLUDE_PATTERN = Pattern.compile(
            ".*(?:不|别|不要|为什么|怎么|如何|能否|可以|会|什么).*(?:退出|离开|走|退下|结束).*"
            + "|.*(?:don't|not).*(?:leave|exit|quit|bye).*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 检测输入文本是否包含退出意图
     *
     * @param input 用户输入的文本
     * @return 如果检测到退出意图返回 true，否则返回 false
     */
    public boolean detectExitIntent(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        // 去除空格和标点符号，统一转为小写
        String normalizedInput = input.trim().toLowerCase();

        // 首先检查排除模式，如果匹配到排除模式则不触发退出
        if (EXCLUDE_PATTERN.matcher(normalizedInput).matches()) {
            return false;
        }

        // 检查精确匹配模式
        if (EXIT_PATTERN.matcher(normalizedInput).matches()) {
            return true;
        }

        // 检查简单关键词（适用于单独的短消息）
        // 只有当输入很短（少于15个字符）时才使用简单关键词匹配
        if (normalizedInput.length() <= 15) {
            for (String keyword : EXIT_KEYWORDS) {
                if (normalizedInput.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        }

        return false;
    }

}

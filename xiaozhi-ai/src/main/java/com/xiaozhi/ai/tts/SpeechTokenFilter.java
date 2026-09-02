package com.xiaozhi.ai.tts;

import com.xiaozhi.utils.EmojiUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 送语音合成前的 token 过滤：整组去掉括号内的舞台指示和方括号元数据标签，Markdown 链接只留文字。
 * 开括号出现后暂存后续 token，闭合后整组处理再放行；方括号刚闭合时多等一个 token，紧跟 (url) 的按链接处理；
 * 暂存超过 MAX_HOLD_CHARS 字仍未闭合的按普通文本放行。有状态实例，一条流一个。
 */
public final class SpeechTokenFilter {
    public static final int MAX_HOLD_CHARS = 60;

    private final StringBuilder held = new StringBuilder();

    public static Flux<String> apply(Flux<String> tokens) {
        return Flux.defer(() -> {
            SpeechTokenFilter filter = new SpeechTokenFilter();
            return tokens.<String>handle((token, sink) -> {
                        String out = filter.accept(token);
                        if (!out.isEmpty()) {
                            sink.next(out);
                        }
                    })
                    .concatWith(Mono.fromSupplier(filter::flush).filter(rest -> !rest.isEmpty()));
        });
    }

    /**
     * 处理一个 token，返回本次可放行的文本
     */
    String accept(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        held.append(token);
        String text = clean(held.toString());
        held.setLength(0);
        int from = holdFrom(text);
        if (from < 0 || text.length() - from > MAX_HOLD_CHARS) {
            return text;
        }
        held.append(text, from, text.length());
        return text.substring(0, from);
    }

    private static String clean(String text) {
        return EmojiUtils.stripParentheses(EmojiUtils.stripMetaTags(EmojiUtils.stripLinks(text)));
    }

    /**
     * 流结束时放行暂存的未闭合文本
     */
    String flush() {
        String rest = held.toString();
        held.setLength(0);
        return rest;
    }

    /** 需要暂存的起点：未闭合的最后一个开括号，或刚闭合的方括号组；无需暂存返回 -1 */
    private static int holdFrom(String text) {
        if (text.endsWith("]")) {
            return bracketStart(text, text.lastIndexOf('['));
        }
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '(' || c == '（') {
                if (hasCloserAfter(text, i, ")）")) {
                    return -1;
                }
                if (i > 0 && text.charAt(i - 1) == ']') {
                    int bracket = text.lastIndexOf('[', i);
                    return bracket >= 0 ? bracketStart(text, bracket) : i;
                }
                return i;
            }
            if (c == '[') {
                return hasCloserAfter(text, i, "]") ? -1 : bracketStart(text, i);
            }
        }
        return -1;
    }

    /** 方括号前紧邻的 ! 属于图片语法，一起暂存 */
    private static int bracketStart(String text, int bracket) {
        if (bracket > 0 && text.charAt(bracket - 1) == '!') {
            return bracket - 1;
        }
        return bracket;
    }

    private static boolean hasCloserAfter(String text, int from, String closers) {
        for (int i = from + 1; i < text.length(); i++) {
            if (closers.indexOf(text.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }
}

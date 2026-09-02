package com.xiaozhi.ai.tts;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 括号舞台指示和方括号元数据标签会被拆成多个 token 到达，逐 token 清洗拦不住，
 * 分句器还会在括号内的逗号处切句，把半截舞台指示送去合成。
 */
class SpeechTokenFilterTest {

    private static String join(String... tokens) {
        List<String> out = SpeechTokenFilter.apply(Flux.fromArray(tokens)).collectList().block();
        return String.join("", out);
    }

    @Test
    void stageDirectionSplitAcrossTokensIsRemoved() {
        assertThat(join("（轻轻", "歪头，声音", "带着点俏皮）", "你好呀")).isEqualTo("你好呀");
    }

    @Test
    void textBeforeOpeningParenthesisIsReleasedImmediately() {
        List<String> out = SpeechTokenFilter.apply(Flux.just("你好（", "笑）", "呀")).collectList().block();

        assertThat(out).containsExactly("你好", "呀");
    }

    @Test
    void leadingMetaTagSplitAcrossTokensIsRemoved() {
        assertThat(join("[", "neutral", "] 你刚刚", "在说什么？")).isEqualTo("你刚刚在说什么？");
    }

    @Test
    void ordinaryBracketsPassThroughIntact() {
        List<String> out = SpeechTokenFilter.apply(Flux.just("[", "1", "] 第一点")).collectList().block();

        assertThat(out).containsExactly("[1] 第一点");
    }

    @Test
    void markdownLinkSplitAcrossTokensKeepsTextOnly() {
        assertThat(join("详情见[", "官方文档", "](https", "://example.com/a)", "。")).isEqualTo("详情见官方文档。");
        assertThat(join("详情见[官方文档]", "(https://example.com/a)。")).isEqualTo("详情见官方文档。");
    }

    @Test
    void markdownImageIsRemoved() {
        assertThat(join("看图![示意", "图](https://example.com/x.png)好看吧")).isEqualTo("看图好看吧");
    }

    @Test
    void unclosedParenthesisIsReleasedAfterHoldLimit() {
        String longTail = "一".repeat(SpeechTokenFilter.MAX_HOLD_CHARS);

        assertThat(join("（", longTail)).isEqualTo("（" + longTail);
    }

    @Test
    void unclosedParenthesisIsFlushedWhenStreamEnds() {
        assertThat(join("这句话（没有闭合")).isEqualTo("这句话（没有闭合");
    }

    @Test
    void emptyTokensProduceNothing() {
        List<String> out = SpeechTokenFilter.apply(Flux.just("", "（笑）")).collectList().block();

        assertThat(out).isEmpty();
    }
}

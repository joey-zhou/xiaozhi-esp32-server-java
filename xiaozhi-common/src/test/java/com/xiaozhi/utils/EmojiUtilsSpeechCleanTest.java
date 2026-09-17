package com.xiaozhi.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 送 TTS 的文本要去掉 Markdown 结构，否则星号、反引号、URL 都会被念出来。
 * 只影响 text4Speech，设备端显示的原文不变。
 */
class EmojiUtilsSpeechCleanTest {

    private static String speech(String text) {
        return EmojiUtils.processSentence(text, new ArrayList<>());
    }

    @Test
    void linkKeepsTextAndDropsUrl() {
        assertThat(speech("详情见[官方文档](https://example.com/a_b-c)。")).isEqualTo("详情见官方文档。");
    }

    @Test
    void imageIsRemovedEntirely() {
        assertThat(speech("这是图片![示意图](https://example.com/x.png)好看吧")).isEqualTo("这是图片好看吧");
    }

    @Test
    void inlineCodeKeepsContent() {
        assertThat(speech("执行 `npm install` 就好")).isEqualTo("执行 npm install 就好");
    }

    @Test
    void codeFenceMarkersAreRemoved() {
        assertThat(speech("```java\n你好\n```")).isEqualTo("你好");
    }

    @Test
    void listAndQuotePrefixesAreRemoved() {
        assertThat(speech("- 第一条\n- 第二条")).isEqualTo("第一条 第二条");
        assertThat(speech("1. 先这样\n2. 再那样")).isEqualTo("先这样 再那样");
        assertThat(speech("> 引用的话")).isEqualTo("引用的话");
    }

    @Test
    void emphasisMarkersAreRemoved() {
        assertThat(speech("这很**重要**，也很~~不重要~~，还有_强调_")).isEqualTo("这很重要，也很不重要，还有强调");
    }

    @Test
    void dividerLineIsRemoved() {
        assertThat(speech("上文\n---\n下文")).isEqualTo("上文 下文");
    }

    @Test
    void plainTextIsUntouched() {
        assertThat(speech("今天天气不错，你说呢？")).isEqualTo("今天天气不错，你说呢？");
    }

    @Test
    void parenthesesAreRemovedRegardlessOfLength() {
        assertThat(speech("（她眨了眨眼睛，语气变得温柔起来，仿佛在安慰一个受伤的小动物）你别难过啦"))
                .isEqualTo("你别难过啦");
        assertThat(speech("因为它们都台了啊（台=呆，谐音梗）")).isEqualTo("因为它们都台了啊");
        assertThat(speech("这是英文 (aside) 括号")).isEqualTo("这是英文 括号");
    }

    @Test
    void unclosedParenthesisIsKept() {
        assertThat(speech("（指尖划过空气，")).isEqualTo("（指尖划过空气，");
    }

    @Test
    void metaTagsAreRemoved() {
        assertThat(speech("[neutral] 你刚刚在说什么？")).isEqualTo("你刚刚在说什么？");
        assertThat(speech("[2026-09-02T13:51:42][说话人:张三][happy] 今天真开心")).isEqualTo("今天真开心");
    }

    @Test
    void ordinaryBracketsAreKept() {
        assertThat(speech("[1] 第一点")).isEqualTo("[1] 第一点");
        assertThat(speech("[API] 接口说明")).isEqualTo("[API] 接口说明");
    }

    // 情绪标签只认已知的那批。放宽成任意小写词的话，模型回答里的 [apple]、arr[index] 会被静默删掉，
    // 而且清洗后的文本还会入库，脏文本进入模型自己的历史
    @Test
    void bracketedOrdinaryWordsAreNotTreatedAsEmotionTags() {
        assertThat(speech("列表 [apple] 和 [banana] 都不错")).isEqualTo("列表 [apple] 和 [banana] 都不错");
        assertThat(speech("请用 arr[index] 取值")).isEqualTo("请用 arr[index] 取值");
        assertThat(speech("[here] 是说明")).isEqualTo("[here] 是说明");
    }

    @Test
    void emojiStillExtractedAsMood() {
        List<String> moods = new ArrayList<>();
        String result = EmojiUtils.processSentence("**太好了**😀", moods);

        assertThat(result).isEqualTo("太好了");
        // 😀 在 EmojiUtils 的映射表里唯一属于 laughing
        assertThat(moods).containsExactly("laughing");
    }

    @Test
    void supplementaryPlaneCharactersAreKeptAsSingleCodePoint() {
        // 𝐀(U+1D400) 与 𫝀(U+2A740) 都是增补平面字符且不属于任何表情区段，
        // 逐 char 而非逐码点推进会把它们拆成孤立代理项，导致输出比输入还长
        String input = "公式𝐀和𫝀结束";
        String result = EmojiUtils.processSentence(input, new ArrayList<>());

        assertThat(result).isEqualTo(input);
        // 拆错时增补平面字符会退化成落在 U+D800-U+DFFF 的孤立代理项，正确配对时合成单个码点不落在这个区间
        assertThat(result.codePoints()
                .noneMatch(cp -> cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE)).isTrue();
    }

    @Test
    void cjkExtensionBIdeographIsKeptNotTreatedAsEmoji() {
        // U+20000 属于 CJK 扩展 B（生僻汉字），不是表情，不能被当作表情删掉
        assertThat(EmojiUtils.isEmoji(0x20000)).isFalse();

        List<String> moods = new ArrayList<>();
        String result = EmojiUtils.processSentence("名字𠀋结束", moods);

        assertThat(result).isEqualTo("名字𠀋结束");
        assertThat(moods).isEmpty();
    }
}

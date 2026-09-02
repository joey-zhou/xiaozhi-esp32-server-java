package com.xiaozhi.ai.tts;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 钉住分句状态机的规则：句末标点要跨 token 等右引号/右括号再收句，否则引号会被甩到下一句开头，
 * 半句未闭合的引号送给 TTS 会被误判为没说完；停顿/冒号/表情触发的切句必须够长才切，
 * 清洗后不成句的缓冲必须继续累计而不是丢掉，否则字幕会缺字。
 */
class SentenceHelperSegmentationTest {

    @Test
    void endMarkWaitsAcrossTokensAndMergesClosingQuote() {
        SentenceHelper helper = new SentenceHelper();

        // 句末标点后先挂起，等下一个字符决定收尾符号是否并入本句
        assertThat(helper.take("你好呀今天天气不错。")).isEmpty();

        List<SentenceHelper.SentenceResult> sentences = helper.take("”明天见");

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).text()).isEqualTo("你好呀今天天气不错。”");
    }

    @Test
    void endMarkSplitsBeforeNonClosingCharacter() {
        SentenceHelper helper = new SentenceHelper();

        List<SentenceHelper.SentenceResult> sentences = helper.take("你好呀今天天气不错。明天");

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).text()).isEqualTo("你好呀今天天气不错。");
        assertThat(helper.take().text()).isEqualTo("明天");
    }

    @Test
    void pauseMarkSplitsOnlyAfterMinimumLength() {
        SentenceHelper helper = new SentenceHelper();

        assertThat(helper.take("短，")).isEmpty();

        List<SentenceHelper.SentenceResult> sentences = helper.take("这是一段较长的话，");

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).text()).isEqualTo("短，这是一段较长的话，");
    }

    @Test
    void colonSplitsWhenSentenceLongEnough() {
        SentenceHelper helper = new SentenceHelper();

        List<SentenceHelper.SentenceResult> sentences = helper.take("下面是重点内容：");

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).text()).isEqualTo("下面是重点内容：");
    }

    @Test
    void newlineSplitsWhenSentenceLongEnough() {
        SentenceHelper helper = new SentenceHelper();

        List<SentenceHelper.SentenceResult> sentences = helper.take("这是一段够长的文字\n");

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).text()).isEqualTo("这是一段够长的文字");
    }

    @Test
    void emojiSplitsSentenceAndExtractsMood() {
        SentenceHelper helper = new SentenceHelper();

        List<SentenceHelper.SentenceResult> sentences = helper.take("今天天气真是不错啊😊");

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).text()).isEqualTo("今天天气真是不错啊");
        assertThat(sentences.get(0).mood()).isEqualTo("happy");
    }

    @Test
    void closedParenthesesSplitSentenceAndAreStripped() {
        SentenceHelper helper = new SentenceHelper();

        List<SentenceHelper.SentenceResult> sentences = helper.take("今天天气真是不错啊（很晴朗）");

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).text()).isEqualTo("今天天气真是不错啊");
    }

    @Test
    void sentenceTooShortAfterCleaningKeepsBufferForNextToken() {
        SentenceHelper helper = new SentenceHelper();

        // 表情触发切句，但去掉表情后只剩 7 个字，不足以成句，缓冲必须保留
        assertThat(helper.take("今天天气真不错😊")).isEmpty();

        List<SentenceHelper.SentenceResult> sentences = helper.take("，真舒服");

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).text()).isEqualTo("今天天气真不错，");
        assertThat(sentences.get(0).mood()).isEqualTo("happy");
    }

    // 当前行为：句末标点集合是 [。！？!?]，不含英文句号，小数点保护分支永远进不去，
    //          英文句号也从不切句，整段英文回复只能靠流结束时的 take() 一次性刷出
    // 正确行为：'.' 应作为句末标点参与切句，并由小数点保护把 3.14 这类数字排除
    // 生产代码 xiaozhi-ai/src/main/java/com/xiaozhi/ai/tts/SentenceHelper.java:29
    @Test
    void periodNeverSplitsSoDecimalGuardIsUnreachable() {
        SentenceHelper chinese = new SentenceHelper();
        SentenceHelper english = new SentenceHelper();

        assertThat(chinese.take("圆周率大约等于3.14159这个值")).isEmpty();
        assertThat(english.take("This is a fairly long sentence.")).isEmpty();

        assertThat(chinese.take().text()).isEqualTo("圆周率大约等于3.14159这个值");
    }

    // 当前行为：缓冲里只有空白且长度够触发切句时，trim 后的空串送进 EmojiUtils.processSentence
    //          被 Assert.hasText 拒绝，IllegalArgumentException 直接抛出 take，打断整条 TTS 流
    // 正确行为：trim 后为空应视为无实质内容，静默丢弃缓冲继续累计
    // 生产代码 xiaozhi-ai/src/main/java/com/xiaozhi/ai/tts/SentenceHelper.java:159
    @Test
    void whitespaceOnlyBufferThrowsOnFlush() {
        SentenceHelper helper = new SentenceHelper();

        assertThatThrownBy(() -> helper.take("        \n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankTokenProducesNothing() {
        SentenceHelper helper = new SentenceHelper();

        assertThat(helper.take(null)).isEmpty();
        assertThat(helper.take("")).isEmpty();
        assertThat(helper.take().text()).isEmpty();
    }
}

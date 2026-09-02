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

    // 英文句点参与切句，否则整段英文只能等流结束一次性刷出，首句延迟等于整段生成时间
    @Test
    void englishPeriodFollowedBySpaceSplitsSentence() {
        SentenceHelper helper = new SentenceHelper();

        List<SentenceHelper.SentenceResult> sentences =
                helper.take("This is a fairly long sentence. And here comes the next one.");

        assertThat(sentences).hasSize(1);
        assertThat(sentences.get(0).text()).isEqualTo("This is a fairly long sentence.");
        assertThat(helper.take().text()).isEqualTo("And here comes the next one.");
    }

    // 句点后面还接着内容时不切句：小数、域名、缩写都不能被拆开
    @Test
    void periodInsideNumberOrDomainDoesNotSplit() {
        SentenceHelper decimal = new SentenceHelper();
        SentenceHelper domain = new SentenceHelper();
        SentenceHelper abbreviation = new SentenceHelper();

        assertThat(decimal.take("圆周率大约等于3.14159这个值")).isEmpty();
        assertThat(domain.take("请访问 example.com 查看详情")).isEmpty();
        assertThat(abbreviation.take("他来自 U.S.A 这个国家")).isEmpty();

        assertThat(decimal.take().text()).isEqualTo("圆周率大约等于3.14159这个值");
        assertThat(domain.take().text()).isEqualTo("请访问 example.com 查看详情");
    }

    // 句点跨 token 到达时状态要保持，下一个 token 的首字符才决定切不切
    @Test
    void periodAtTokenBoundaryIsConfirmedByNextToken() {
        SentenceHelper split = new SentenceHelper();
        SentenceHelper joined = new SentenceHelper();

        assertThat(split.take("This is a fairly long sentence.")).isEmpty();
        assertThat(split.take(" Next.")).hasSize(1);

        assertThat(joined.take("Version is 1.")).isEmpty();
        assertThat(joined.take("2.3 released")).isEmpty();
    }

    // 纯空白缓冲不能送进文本清洗，否则 Assert.hasText 抛异常打断整条合成流
    @Test
    void whitespaceOnlyBufferIsDiscardedWithoutThrowing() {
        SentenceHelper helper = new SentenceHelper();

        assertThat(helper.take("        \n")).isEmpty();
        assertThat(helper.take().text()).isEmpty();

        // 空白已被丢弃，不会残留到下一句
        helper.take("接着说正经内容。");
        assertThat(helper.take().text()).isEqualTo("接着说正经内容。");
    }

    @Test
    void blankTokenProducesNothing() {
        SentenceHelper helper = new SentenceHelper();

        assertThat(helper.take(null)).isEmpty();
        assertThat(helper.take("")).isEmpty();
        assertThat(helper.take().text()).isEmpty();
    }
}

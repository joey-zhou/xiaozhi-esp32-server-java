package com.xiaozhi.ai.stt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 热词是用户在网页里手敲的自由文本，一行写错不能让整份配置作废——那样用户只会看到"热词没生效"
 * 而不知道是哪一行的问题。解析规则同时决定了各 provider 收到什么，改动前先看这里钉住的边界。
 */
class HotwordTest {

    @Test
    void parseReturnsEmptyForBlankInput() {
        assertThat(Hotword.parse(null)).isEmpty();
        assertThat(Hotword.parse("   \n  \n")).isEmpty();
    }

    @Test
    void parseAssignsDefaultWeightWhenOmitted() {
        List<Hotword> hotwords = Hotword.parse("泽宇\n小米音箱");

        assertThat(hotwords).containsExactly(
                new Hotword("泽宇", Hotword.DEFAULT_WEIGHT),
                new Hotword("小米音箱", Hotword.DEFAULT_WEIGHT));
    }

    @Test
    void parseReadsTrailingWeight() {
        assertThat(Hotword.parse("泽宇 11")).containsExactly(new Hotword("泽宇", 11));
    }

    @Test
    void parseKeepsSpacesInsideTheWord() {
        // 专有名词自带空格是常态，只有最后一段是纯数字时才当权重
        assertThat(Hotword.parse("AirPods Pro")).containsExactly(
                new Hotword("AirPods Pro", Hotword.DEFAULT_WEIGHT));
        assertThat(Hotword.parse("Model Y 11")).containsExactly(new Hotword("Model Y", 11));
    }

    @Test
    void parseSkipsOutOfRangeWeightInsteadOfClamping() {
        // 权重超范围时整行当普通词处理，数字留在词里比悄悄改成别的权重更容易被用户发现
        assertThat(Hotword.parse("泽宇 99")).containsExactly(
                new Hotword("泽宇 99", Hotword.DEFAULT_WEIGHT));
    }

    @Test
    void parseDropsOverlongWordWithoutDiscardingTheRest() {
        String tooLong = "长".repeat(Hotword.MAX_TEXT_LENGTH + 1);

        assertThat(Hotword.parse(tooLong + "\n泽宇")).containsExactly(
                new Hotword("泽宇", Hotword.DEFAULT_WEIGHT));
    }

    @Test
    void parseKeepsFirstOccurrenceOfDuplicatedWord() {
        assertThat(Hotword.parse("泽宇 11\n泽宇 5")).containsExactly(new Hotword("泽宇", 11));
    }

    @Test
    void parseStopsAtMaxCount() {
        String raw = IntStream.rangeClosed(1, Hotword.MAX_COUNT + 20)
                .mapToObj(i -> "词" + i)
                .collect(Collectors.joining("\n"));

        assertThat(Hotword.parse(raw)).hasSize(Hotword.MAX_COUNT);
    }

    @Test
    void limitTruncatesToProviderCap() {
        List<Hotword> hotwords = Hotword.parse("甲\n乙\n丙");

        assertThat(Hotword.limit(hotwords, 2)).containsExactly(
                new Hotword("甲", Hotword.DEFAULT_WEIGHT),
                new Hotword("乙", Hotword.DEFAULT_WEIGHT));
        assertThat(Hotword.limit(hotwords, 10)).isEqualTo(hotwords);
        assertThat(Hotword.limit(List.of(), 10)).isEmpty();
    }
}

package com.xiaozhi.ai.stt.providers;

import com.xiaozhi.ai.stt.Hotword;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 热词的存储格式与 provider 无关，三家的线上格式差别很大且都靠字符串拼装：腾讯是
 * {@code 词|权重} 逗号分隔的一整串，FunASR 是塞在开场消息里的 JSON 字符串，
 * 火山是 {@code request.context} 里的 JSON 字符串且不收权重。
 * 格式写错不会报错，只会安静地不生效，只能靠用例钉住。
 */
class SttHotwordMappingTest {

    @Test
    void tencentJoinsWordsWithWeightsSeparatedByComma() {
        String list = tencentHotwordList(Hotword.parse("泽宇\n小米音箱 11"));

        assertThat(list).isEqualTo("泽宇|10,小米音箱|11");
    }

    @Test
    void tencentReturnsNullWhenThereIsNoHotword() {
        // 返回 null 时调用方不设该参数，不能发一个空串过去
        assertThat(tencentHotwordList(List.of())).isNull();
    }

    @Test
    void tencentSkipsWordsThatWouldBreakTheSeparator() {
        assertThat(tencentHotwordList(Hotword.parse("甲,乙\n丙|丁\n泽宇"))).isEqualTo("泽宇|10");
    }

    @Test
    void tencentTruncatesBeyondItsOwnCap() {
        List<Hotword> hotwords = IntStream.rangeClosed(1, 200)
                .mapToObj(i -> new Hotword("词" + i, Hotword.DEFAULT_WEIGHT))
                .toList();

        assertThat(tencentHotwordList(hotwords).split(",")).hasSize(128);
    }

    @Test
    void funasrPutsHotwordsAsJsonStringInStartMessage() {
        JsonNode start = JsonUtil.fromJson(funasrSpeakingStart(Hotword.parse("泽宇\n小米音箱 11")), JsonNode.class);

        assertThat(start.path("mode").asText()).isEqualTo("2pass");
        // 协议要求这里是个字符串而不是嵌套对象
        assertThat(start.path("hotwords").isTextual()).isTrue();
        JsonNode weights = JsonUtil.fromJson(start.path("hotwords").asText(), JsonNode.class);
        assertThat(weights.path("泽宇").asInt()).isEqualTo(10);
        assertThat(weights.path("小米音箱").asInt()).isEqualTo(11);
    }

    @Test
    void funasrOmitsHotwordsFieldWhenThereIsNone() {
        JsonNode start = JsonUtil.fromJson(funasrSpeakingStart(List.of()), JsonNode.class);

        assertThat(start.has("hotwords")).isFalse();
        // 没有热词时其余开场参数必须与改造前逐字相同
        assertThat(start.path("wav_name").asText()).isEqualTo("voice.wav");
        assertThat(start.path("is_speaking").asBoolean()).isTrue();
        assertThat(start.path("wav_format").asText()).isEqualTo("pcm");
        assertThat(start.path("itn").asBoolean()).isTrue();
        assertThat(start.path("chunk_size").toString()).isEqualTo("[5,10,5]");
    }

    @Test
    void volcengineWrapsWordsInContextJsonWithoutWeights() {
        JsonNode context = JsonUtil.fromJson(volcengineContext(Hotword.parse("泽宇\n小米音箱 11")), JsonNode.class);

        assertThat(context.path("hotwords")).hasSize(2);
        assertThat(context.path("hotwords").get(0).path("word").asText()).isEqualTo("泽宇");
        assertThat(context.path("hotwords").get(1).path("word").asText()).isEqualTo("小米音箱");
        // 火山这条口不收权重，多塞字段会被判为非法参数
        assertThat(context.path("hotwords").get(1).has("weight")).isFalse();
    }

    @Test
    void volcengineReturnsNullWhenThereIsNoHotword() {
        // 返回 null 时不设 context 字段，不能发一个空 JSON 过去
        assertThat(volcengineContext(List.of())).isNull();
    }

    @Test
    void volcengineTruncatesBeyondItsTokenBudget() {
        List<Hotword> hotwords = IntStream.rangeClosed(1, 200)
                .mapToObj(i -> new Hotword("词" + i, Hotword.DEFAULT_WEIGHT))
                .toList();

        JsonNode context = JsonUtil.fromJson(volcengineContext(hotwords), JsonNode.class);

        assertThat(context.path("hotwords")).hasSize(60);
    }

    private static String tencentHotwordList(List<Hotword> hotwords) {
        return ReflectionTestUtils.invokeMethod(TencentSttService.class, "toHotwordList", hotwords);
    }

    private static String funasrSpeakingStart(List<Hotword> hotwords) {
        return ReflectionTestUtils.invokeMethod(FunASRSttService.class, "buildSpeakingStart", hotwords);
    }

    /** 火山的构造只读配置不连网，直接 new 一个来调它的实例方法 */
    private static String volcengineContext(List<Hotword> hotwords) {
        VolcengineSttService service = new VolcengineSttService(new ConfigBO().setApiKey("k"));
        return ReflectionTestUtils.invokeMethod(service, "buildHotwordContext", hotwords);
    }
}

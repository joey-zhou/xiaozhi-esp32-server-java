package com.xiaozhi.ai.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住模型输出的几种常见走样：少右括号、截断在字符串或逗号处、带代码围栏、前后夹说明。
 */
class LenientJsonTest {

    @Test
    void missingClosingBracesAreAppended() {
        assertThat(LenientJson.object("{\"facts\": [\"处于叛逆期\", \"天天玩游戏\"]"))
                .isEqualTo("{\"facts\": [\"处于叛逆期\", \"天天玩游戏\"]}");
        assertThat(LenientJson.object("{\"memory\": [{\"id\": 1, \"text\": \"a\""))
                .isEqualTo("{\"memory\": [{\"id\": 1, \"text\": \"a\"}]}");
    }

    @Test
    void truncatedStringAndDanglingCommaAreRepaired() {
        assertThat(LenientJson.complete("{\"facts\": [\"喜欢喝咖")).isEqualTo("{\"facts\": [\"喜欢喝咖\"]}");
        assertThat(LenientJson.complete("{\"facts\": [\"a\", ")).isEqualTo("{\"facts\": [\"a\"]}");
    }

    @Test
    void bracketsInsideStringsAreIgnored() {
        String text = "{\"facts\": [\"括号 { 和 ] 在字符串里\", \"转义 \\\" 引号\"]}";
        assertThat(LenientJson.complete(text)).isEqualTo(text);
    }

    @Test
    void fencesAndSurroundingProseAreStripped() {
        assertThat(LenientJson.object("```json\n{\"facts\": []}\n```")).isEqualTo("{\"facts\": []}");
        assertThat(LenientJson.object("好的，结果如下：{\"facts\": []} 以上。")).isEqualTo("{\"facts\": []}");
    }

    // 收尾多逗号、单引号、裸字段名、字符串里裸换行、注释都归放宽的解析器，不再手写修补
    @Test
    void lenientMapperAcceptsCommonSyntaxSlips() throws Exception {
        String sloppy = """
                {facts: ['第一条',
                  // 模型顺手写的注释
                  "第二
                行",],}""";

        Map<String, List<String>> parsed = LenientJson.mapper().readValue(sloppy, new TypeReference<>() {
        });

        assertThat(parsed.get("facts")).containsExactly("第一条", "第二\n行");
    }

    @Test
    void textWithoutObjectIsReturnedAsIs() {
        assertThat(LenientJson.object("抱歉，我无法处理")).isEqualTo("抱歉，我无法处理");
        assertThat(LenientJson.object(null)).isNull();
    }
}

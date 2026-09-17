package com.xiaozhi.ai.llm;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 估算值只用来触发压缩，钉住的是量级：中文按 Qwen 词表大约每 1.5 个字一个 token，工具调用的参数与返回也要算进去。
 */
class TokenEstimatorTest {

    @Test
    void chineseTextIsCountedByTheQwenVocabulary() {
        int tokens = TokenEstimator.estimate("我妹妹叫小红，她今年上高二，特别喜欢打羽毛球，周末经常约同学去体育馆。");

        assertThat(tokens).isBetween(20, 30);
    }

    @Test
    void blankTextCostsNothing() {
        assertThat(TokenEstimator.estimate((String) null)).isZero();
        assertThat(TokenEstimator.estimate("   ")).isZero();
    }

    @Test
    void everyMessageCarriesTemplateOverhead() {
        UserMessage user = new UserMessage("你好");

        assertThat(TokenEstimator.estimate(user))
                .isEqualTo(TokenEstimator.PER_MESSAGE_OVERHEAD + TokenEstimator.estimate("你好"));
    }

    @Test
    void toolCallsAndToolResponsesCountTheirPayload() {
        AssistantMessage call = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getWeather",
                        "{\"city\":\"上海\",\"date\":\"明天\"}")))
                .build();
        ToolResponseMessage response = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "getWeather",
                        "上海明天多云转小雨，气温 18 到 24 度，东南风三级")))
                .build();

        assertThat(TokenEstimator.estimate(call)).isGreaterThan(TokenEstimator.PER_MESSAGE_OVERHEAD + 5);
        assertThat(TokenEstimator.estimate(response)).isGreaterThan(TokenEstimator.PER_MESSAGE_OVERHEAD + 10);
        assertThat(TokenEstimator.estimate(List.of(call, response)))
                .isEqualTo(TokenEstimator.estimate(call) + TokenEstimator.estimate(response));
    }
}

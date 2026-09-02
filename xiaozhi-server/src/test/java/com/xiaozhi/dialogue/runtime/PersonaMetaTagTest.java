package com.xiaozhi.dialogue.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型模仿用户消息前缀输出的 [neutral] 之类标签一旦进了历史，下一轮会继续模仿。
 */
class PersonaMetaTagTest {

    @Test
    void leadingMetaTagIsStrippedFromHistoryText() {
        AssistantMessage original = AssistantMessage.builder()
                .content("[neutral] 你刚刚在说什么？")
                .properties(Map.of("k", "v"))
                .build();

        AssistantMessage cleaned = Persona.stripMetaTags(original);

        assertThat(cleaned.getText()).isEqualTo("你刚刚在说什么？");
        assertThat(cleaned.getMetadata()).containsEntry("k", "v");
    }

    @Test
    void toolCallsSurviveStripping() {
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("id1", "function", "get_weather", "{}");
        AssistantMessage original = AssistantMessage.builder()
                .content("[happy] 我查一下")
                .toolCalls(List.of(call))
                .build();

        AssistantMessage cleaned = Persona.stripMetaTags(original);

        assertThat(cleaned.getText()).isEqualTo("我查一下");
        assertThat(cleaned.getToolCalls()).containsExactly(call);
    }

    @Test
    void untaggedMessageIsReturnedAsIs() {
        AssistantMessage original = new AssistantMessage("今天天气不错");

        assertThat(Persona.stripMetaTags(original)).isSameAs(original);
    }
}

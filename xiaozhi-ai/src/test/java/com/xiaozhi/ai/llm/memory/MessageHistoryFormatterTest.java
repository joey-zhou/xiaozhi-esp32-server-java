package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.MessageMetadataBO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 摘要拿到的批次是裸消息，元数据都在 metadata 里。渲染时必须装配成「[时间][情绪] 正文」，
 * 否则摘要模型看到的对话没有时间也没有情绪，和对话时主模型看到的不是同一份。
 */
class MessageHistoryFormatterTest {

    private static UserMessage userMessage(String text, String emotion) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ChatMemory.TIME_MILLIS_KEY, Instant.parse("2026-09-19T02:00:00Z"));
        MessageMetadataBO bo = new MessageMetadataBO();
        bo.setEmotion(emotion);
        metadata.put(MessageMetadataBO.METADATA_KEY, bo);
        return UserMessage.builder().text(text).metadata(metadata).build();
    }

    @Test
    void metadataPrefixIsRenderedIntoEachUserLine() {
        String rendered = MessageHistoryFormatter.format(List.of(
                userMessage("我怕打雷", "fearful"),
                new AssistantMessage("打雷的时候我陪着你")));

        assertThat(rendered).contains("[fearful] 我怕打雷");
        assertThat(rendered).contains("ASSISTANT:打雷的时候我陪着你");
    }

    @Test
    void messagesWithoutMetadataAreRenderedAsBefore() {
        String rendered = MessageHistoryFormatter.format(List.of(
                new UserMessage("几点了"), new AssistantMessage("八点")));

        assertThat(rendered).isEqualTo("USER:几点了" + System.lineSeparator() + "ASSISTANT:八点");
    }

    @Test
    void toolCallsStillCollapseToNamesOnly() {
        AssistantMessage withCall = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", "get_weather", "{}")))
                .build();

        String rendered = MessageHistoryFormatter.format(List.<Message>of(withCall));

        assertThat(rendered).isEqualTo("ASSISTANT:[tool_call:get_weather]");
    }
}

package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.SummaryBO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 首次摘要与续写摘要用不同提示词，续写要把上一版带进去；落库的摘要带对话归属与本批最后一条消息的时间，
 * 下次建对话据此只加载摘要之后的消息。
 */
class LlmSummarizerTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ChatMemory chatMemory = mock(ChatMemory.class);

    private LlmSummarizer summarizer(String ownerId, String sessionId) {
        return new LlmSummarizer(chatModel,
                template("首次摘要 $datetime$" + System.lineSeparator() + "$conversation$"),
                template("续写摘要 $last_summary$" + System.lineSeparator() + "$conversation$"),
                chatMemory, ownerId, 1, sessionId);
    }

    private static PromptTemplate template(String text) {
        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('$').endDelimiterToken('$').build())
                .template(text)
                .build();
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void firstSummaryUsesInitPromptAndIsSavedUnderTheSession() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("用户喜欢篮球"));
        Instant lastAt = Instant.parse("2026-09-14T08:00:00Z");
        AssistantMessage last = new AssistantMessage("篮球很好玩");
        MessageTimeMetadata.setTimeMillis(last, lastAt);

        String result = summarizer("web:9", "session-1").summarize(null, List.of(new UserMessage("我喜欢篮球"), last));

        assertThat(result).isEqualTo("用户喜欢篮球");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains("首次摘要").contains("我喜欢篮球");
        ArgumentCaptor<SummaryBO> saved = ArgumentCaptor.forClass(SummaryBO.class);
        verify(chatMemory).save(saved.capture());
        assertThat(saved.getValue().getDeviceId()).isEqualTo("web:9");
        assertThat(saved.getValue().getRoleId()).isEqualTo(1);
        assertThat(saved.getValue().getSessionId()).isEqualTo("session-1");
        assertThat(saved.getValue().getLastMessageTimestamp()).isEqualTo(lastAt);
        assertThat(saved.getValue().getSummary()).isEqualTo("用户喜欢篮球");
    }

    @Test
    void laterSummaryCarriesThePreviousOneAndDeviceSummaryHasNoSession() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("用户喜欢篮球和足球"));

        summarizer("device-1", null).summarize("上次聊到篮球", List.of(new UserMessage("我也喜欢足球"), new AssistantMessage("好")));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains("续写摘要").contains("上次聊到篮球").contains("我也喜欢足球");
        ArgumentCaptor<SummaryBO> saved = ArgumentCaptor.forClass(SummaryBO.class);
        verify(chatMemory).save(saved.capture());
        assertThat(saved.getValue().getSessionId()).isNull();
    }

    // 空摘要写进去会把上一版摘要冲掉，必须失败，让这批消息留在上下文里重试
    @Test
    void emptyResponseFailsWithoutSaving() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply(""));

        assertThatThrownBy(() -> summarizer("device-1", null)
                .summarize(null, List.of(new UserMessage("你好"), new AssistantMessage("你好呀"))))
                .isInstanceOf(IllegalStateException.class);
        verify(chatMemory, never()).save(any(SummaryBO.class));
    }
}

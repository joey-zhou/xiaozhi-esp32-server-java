package com.xiaozhi.ai.llm.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备说话时插进来的整句交给模型判定：说不是对设备说的才续播，其余一律按打断。
 * 超时、报错、读不出结果都必须倒向打断——漏掉真指令要用户重说一遍，比多接一句话难受。
 */
class AddresseeClassifierTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final AddresseeClassifier classifier = new AddresseeClassifier();

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private boolean classify(String utterance) {
        return classifier.directedAtDevice(chatModel, List.of(), List.of("从前有座山。"), utterance);
    }


    @Test
    void notDirectedUtteranceResumes() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("{\"directed\": false}"));

        assertThat(classify("对，他这个逻辑有问题")).isFalse();
    }

    @Test
    void directedUtteranceInterrupts() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("{\"directed\": true}"));

        assertThat(classify("换一个故事")).isTrue();
    }

    @Test
    void fencedJsonIsStillParsed() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(reply("```json\n{\"directed\": false}\n```"));

        assertThat(classify("你先别管他")).isFalse();
    }

    @Test
    void missingFieldFallsBackToInterrupt() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("{\"verdict\": \"no\"}"));

        assertThat(classify("随便说点什么")).isTrue();
    }

    @Test
    void modelErrorFallsBackToInterrupt() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("上游 500"));

        assertThat(classify("随便说点什么")).isTrue();
    }

    @Test
    void slowModelFallsBackToInterruptWithoutBlockingPastTheBudget() {
        long timeout = (long) ReflectionTestUtils.getField(AddresseeClassifier.class, "VERDICT_TIMEOUT_MS");
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(timeout * 4);
            return reply("{\"directed\": false}");
        });

        long startedAt = System.currentTimeMillis();
        boolean directed = classify("随便说点什么");
        long elapsed = System.currentTimeMillis() - startedAt;

        assertThat(directed).isTrue();
        assertThat(elapsed).isLessThan(timeout * 3);
    }

    @Test
    void noModelOrEmptyTextSkipsTheCall() {
        assertThat(classifier.directedAtDevice(null, List.of(), List.of(), "有话说")).isTrue();
        assertThat(classifier.directedAtDevice(chatModel, List.of(), List.of(), " ")).isTrue();
    }

    @Test
    void onlyTheTailOfTheDialogueGoesIntoThePrompt() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("{\"directed\": true}"));
        List<Message> history = new ArrayList<>();
        history.add(new SystemMessage("你是一个讲故事的角色，绝不能出现在判定提示词里"));
        for (int i = 1; i <= 4; i++) {
            history.add(new UserMessage("用户第" + i + "句"));
            history.add(new AssistantMessage("助手第" + i + "句"));
        }

        classifier.directedAtDevice(chatModel, history, List.of("从前有座山。"), "换一个故事");

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        String rendered = prompt.getValue().getContents();
        // 8 条对话消息只留尾部 5 条，第 1、2 轮的用户消息被裁掉
        assertThat(rendered).doesNotContain("绝不能出现在判定提示词里");
        assertThat(rendered).doesNotContain("用户第1句").doesNotContain("用户第2句");
        assertThat(rendered).contains("助手第2句").contains("用户第4句").contains("助手第4句");
        assertThat(rendered).contains("从前有座山。").contains("换一个故事");
    }
}

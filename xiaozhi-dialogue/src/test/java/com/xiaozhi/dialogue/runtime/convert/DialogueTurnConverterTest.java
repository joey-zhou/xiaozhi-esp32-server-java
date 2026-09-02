package com.xiaozhi.dialogue.runtime.convert;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.dialogue.runtime.DialogueTurn;
import com.xiaozhi.dialogue.runtime.ToolChainPair;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 一个字没播出就被打断的轮次只剩用户消息，落库不能再硬造一条助手记录；
 * 助手消息带的耗时指标按原始时间戳算，不受落库时间截秒影响。
 */
class DialogueTurnConverterTest {

    private final DialogueTurnConverter converter = new DialogueTurnConverter();
    private final Conversation conversation = new Conversation("device", 1, "session", "role", 1);

    @Test
    void turnWithoutAssistantPersistsUserOnly() {
        Instant now = Instant.now();
        DialogueTurn turn = DialogueTurn.builder()
                .userMessage(new UserMessage("讲个故事"))
                .conversation(conversation)
                .userMessageCreatedAt(now)
                .userSpeechStoredPath("audio/user.wav")
                .interrupted(true)
                .build();

        List<MessageBO> messages = converter.toMessages(turn);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getSender()).isEqualTo(MessageBO.SENDER_USER);
        assertThat(messages.get(0).getAudioPath()).isEqualTo("audio/user.wav");
    }

    @Test
    void toolChainsFallBackToUserTimeWhenNoAssistant() {
        Instant now = Instant.now();
        DialogueTurn turn = DialogueTurn.builder()
                .userMessage(new UserMessage("天气"))
                .conversation(conversation)
                .userMessageCreatedAt(now)
                .toolChains(List.of(new ToolChainPair(
                        AssistantMessage.builder()
                                .toolCalls(List.of(new AssistantMessage.ToolCall("c1", "function", "weather", "{}")))
                                .build(),
                        ToolResponseMessage.builder()
                                .responses(List.of(new ToolResponseMessage.ToolResponse("c1", "weather", "晴")))
                                .build())))
                .interrupted(true)
                .build();

        List<MessageBO> messages = converter.toMessages(turn);

        LocalDateTime expected = LocalDateTime.ofInstant(now.truncatedTo(ChronoUnit.SECONDS), ZoneId.systemDefault());
        assertThat(messages).hasSize(3);
        assertThat(messages.get(1).getMessageType()).isEqualTo(MessageBO.MESSAGE_TYPE_TOOL_CALL);
        assertThat(messages.get(1).getCreateTime()).isEqualTo(expected);
        assertThat(messages.get(2).getMessageType()).isEqualTo(MessageBO.MESSAGE_TYPE_TOOL_RESPONSE);
    }

    @Test
    void completedTurnStillWritesAssistant() {
        Instant now = Instant.now();
        DialogueTurn turn = DialogueTurn.builder()
                .userMessage(new UserMessage("你好"))
                .assistantMessage(new AssistantMessage("你好呀"))
                .conversation(conversation)
                .userMessageCreatedAt(now)
                .assistantMessageCreatedAt(now.plusMillis(300))
                .build();

        List<MessageBO> messages = converter.toMessages(turn);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).getSender()).isEqualTo(MessageBO.SENDER_ASSISTANT);
    }
}

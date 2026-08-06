package com.xiaozhi.ai.llm.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTest {

    @Test
    void discardsOnlyMessagesAfterLastCompletedAssistant() {
        Conversation conversation = new Conversation("device", 1, "session", "role", 1);
        UserMessage completedUser = new UserMessage("上一轮");
        AssistantMessage completedAssistant = new AssistantMessage("上一轮回复");
        conversation.add(completedUser);
        conversation.add(completedAssistant);

        conversation.add(new UserMessage("被打断的问题"));
        conversation.addToolCallChain(
                AssistantMessage.builder()
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-1", "function", "get-weather", "{}")))
                        .build(),
                ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                "call-1", "get-weather", "sunny")))
                        .build());

        conversation.discardIncompleteTurn();

        assertThat(conversation.rawMessages()).containsExactly(completedUser, completedAssistant);
    }

    @Test
    void keepsCompletedConversationUnchanged() {
        Conversation conversation = new Conversation("device", 1, "session", "role", 1);
        conversation.add(new UserMessage("你好"));
        conversation.add(new AssistantMessage("你好"));

        conversation.discardIncompleteTurn();

        assertThat(conversation.rawMessages()).hasSize(2);
    }
}

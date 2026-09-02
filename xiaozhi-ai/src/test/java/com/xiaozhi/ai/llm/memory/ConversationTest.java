package com.xiaozhi.ai.llm.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTest {

    @Test
    void replaceSwapsMessageInPlace() {
        Conversation conversation = new Conversation("device", 1, "session", "role", 1);
        UserMessage user = new UserMessage("讲个故事");
        AssistantMessage full = new AssistantMessage("从前有座山。山里有座庙。");
        conversation.add(user);
        conversation.add(full);

        AssistantMessage truncated = new AssistantMessage("从前有座山。");
        conversation.replace(full, truncated);

        assertThat(conversation.rawMessages()).containsExactly(user, truncated);
    }

    @Test
    void replaceIgnoresMessageNotInHistory() {
        Conversation conversation = new Conversation("device", 1, "session", "role", 1);
        UserMessage user = new UserMessage("你好");
        conversation.add(user);

        conversation.replace(new AssistantMessage("不在历史里"), new AssistantMessage("替换"));

        assertThat(conversation.rawMessages()).containsExactly(user);
    }

    @Test
    void insertAfterTurnLandsBeforeNextUserMessage() {
        Conversation conversation = new Conversation("device", 1, "session", "role", 1);
        UserMessage first = new UserMessage("讲个故事");
        UserMessage second = new UserMessage("换一个");
        AssistantMessage secondReply = new AssistantMessage("好的");
        conversation.add(first);
        conversation.add(second);
        conversation.add(secondReply);

        AssistantMessage late = new AssistantMessage("从前有座山。");
        conversation.insertAfterTurn(first, List.of(late));

        assertThat(conversation.rawMessages()).containsExactly(first, late, second, secondReply);
    }

    @Test
    void insertAfterTurnAppendsWhenAnchorMissing() {
        Conversation conversation = new Conversation("device", 1, "session", "role", 1);
        UserMessage user = new UserMessage("你好");
        conversation.add(user);

        AssistantMessage reply = new AssistantMessage("你好呀");
        conversation.insertAfterTurn(new UserMessage("不在历史里"), List.of(reply));

        assertThat(conversation.rawMessages()).containsExactly(user, reply);
    }

    @Test
    void removeDropsOnlyThatMessage() {
        Conversation conversation = new Conversation("device", 1, "session", "role", 1);
        UserMessage user = new UserMessage("你好");
        AssistantMessage assistant = new AssistantMessage("你好呀");
        conversation.add(user);
        conversation.add(assistant);

        conversation.remove(assistant);

        assertThat(conversation.rawMessages()).containsExactly(user);
    }
}

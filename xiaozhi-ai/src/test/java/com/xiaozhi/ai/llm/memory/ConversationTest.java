package com.xiaozhi.ai.llm.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住 Conversation 按对象身份改写历史的三个操作：replace 就地替换、insertAfterTurn 把迟到消息
 * 补回原轮次末尾（工具调用链之后、下一条 UserMessage 之前）、remove 只摘掉指定那一条。
 */
class ConversationTest {

    private static Conversation newConversation() {
        return new Conversation("device", 1, "session", "role", 1);
    }

    /** 一轮工具调用产生的两条消息：带 toolCalls 的 AssistantMessage 和对应的 ToolResponseMessage */
    private static List<Message> newToolChain(String callId) {
        AssistantMessage toolCall = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(callId, "function", "getWeather", "{}")))
                .build();
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(callId, "getWeather", "晴")))
                .build();
        return List.of(toolCall, toolResponse);
    }

    @Test
    void replaceSwapsMessageInPlace() {
        Conversation conversation = newConversation();
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
        Conversation conversation = newConversation();
        UserMessage user = new UserMessage("你好");
        conversation.add(user);

        conversation.replace(new AssistantMessage("不在历史里"), new AssistantMessage("替换"));

        assertThat(conversation.rawMessages()).containsExactly(user);
    }

    @Test
    void insertAfterTurnPutsMessageRightAfterAnchor() {
        Conversation conversation = newConversation();
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

    // 迟到收尾时该轮的工具调用链已经在历史里，补回的回答必须排在工具链之后、下一轮用户消息之前，
    // 否则 assistant 的 toolCalls 与 ToolResponseMessage 之间被插入其它消息，下一轮请求会被模型拒绝。
    @Test
    void insertAfterTurnSkipsToolChainOfSameTurn() {
        Conversation conversation = newConversation();
        UserMessage first = new UserMessage("今天天气怎么样");
        List<Message> toolChain = newToolChain("call-1");
        UserMessage second = new UserMessage("换一个");
        AssistantMessage secondReply = new AssistantMessage("好的");
        conversation.add(first);
        toolChain.forEach(conversation::add);
        conversation.add(second);
        conversation.add(secondReply);

        AssistantMessage late = new AssistantMessage("今天晴。");
        conversation.insertAfterTurn(first, List.of(late));

        assertThat(conversation.rawMessages())
                .containsExactly(first, toolChain.get(0), toolChain.get(1), late, second, secondReply);
    }

    @Test
    void insertAfterTurnAppendsWhenAnchorMissing() {
        Conversation conversation = newConversation();
        UserMessage user = new UserMessage("你好");
        conversation.add(user);

        AssistantMessage reply = new AssistantMessage("你好呀");
        conversation.insertAfterTurn(new UserMessage("不在历史里"), List.of(reply));

        assertThat(conversation.rawMessages()).containsExactly(user, reply);
    }

    @Test
    void removeDropsOnlyThatMessage() {
        Conversation conversation = newConversation();
        UserMessage user = new UserMessage("你好");
        AssistantMessage assistant = new AssistantMessage("你好呀");
        conversation.add(user);
        conversation.add(assistant);

        conversation.remove(assistant);

        assertThat(conversation.rawMessages()).containsExactly(user);
    }
}

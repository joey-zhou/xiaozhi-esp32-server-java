package com.xiaozhi.ai.llm.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 钉住消息窗口的按组裁剪。裁剪只看 messages.get(1) 判断是不是工具对话组，是就一次删 4 条，否则删 2 条。
 * 裁剪后队首必须落在 UserMessage 上：留下孤儿 ToolResponseMessage 或孤儿 tool_call 时，
 * 历史送给 OpenAI / DeepSeek / 通义会直接 400，整轮对话报错、设备静默。
 * 串联多次工具调用、以及工具链还没收到最终回答这两种真实场景当前都会留下孤儿，用例按当前行为钉住。
 */
class MessageWindowTrimTest {

    private static MessageWindowConversation conversation(int maxMessages) {
        return MessageWindowConversation.builder()
                .ownerId("device-1")
                .roleId(1)
                .sessionId("session-1")
                .roleDesc("测试角色")
                .userId(1)
                .maxMessages(maxMessages)
                .chatMemory(mock(ChatMemory.class))
                .sessionScoped(false)
                .build();
    }

    private static AssistantMessage toolCall(String callId, String name) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(callId, "function", name, "{}")))
                .build();
    }

    private static ToolResponseMessage toolResponse(String callId, String name) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(callId, name, "晴")))
                .build();
    }

    private static void addAll(MessageWindowConversation conversation, Message... messages) {
        for (Message message : messages) {
            conversation.add(message);
        }
    }

    @Test
    void trimDropsWholeSimpleTurnsAndPutsSystemPromptFirst() {
        MessageWindowConversation conversation = conversation(2);
        UserMessage lastUser = new UserMessage("第三问");
        AssistantMessage lastReply = new AssistantMessage("第三答");
        addAll(conversation,
                new UserMessage("第一问"), new AssistantMessage("第一答"),
                new UserMessage("第二问"), new AssistantMessage("第二答"),
                lastUser, lastReply);

        List<Message> prompt = conversation.messages(ConversationContext.EMPTY);

        assertThat(prompt.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(prompt.subList(1, prompt.size())).containsExactly(lastUser, lastReply);
        assertThat(conversation.rawMessages()).containsExactly(lastUser, lastReply);
    }

    @Test
    void historyWithinWindowIsNotTrimmed() {
        MessageWindowConversation conversation = conversation(4);
        UserMessage first = new UserMessage("第一问");
        AssistantMessage firstReply = new AssistantMessage("第一答");
        UserMessage second = new UserMessage("第二问");
        AssistantMessage secondReply = new AssistantMessage("第二答");
        addAll(conversation, first, firstReply, second, secondReply);

        conversation.messages();

        assertThat(conversation.rawMessages()).containsExactly(first, firstReply, second, secondReply);
    }

    // 完整的四条工具组 [User, Assistant(toolCall), Tool, Assistant(final)] 被整组删掉，队首回到 UserMessage
    @Test
    void completeToolGroupIsTrimmedAsAWhole() {
        MessageWindowConversation conversation = conversation(2);
        UserMessage nextUser = new UserMessage("谢谢");
        AssistantMessage nextReply = new AssistantMessage("不客气");
        addAll(conversation,
                new UserMessage("今天天气怎么样"), toolCall("call-1", "getWeather"),
                toolResponse("call-1", "getWeather"), new AssistantMessage("今天晴"),
                nextUser, nextReply);

        conversation.messages();

        assertThat(conversation.rawMessages()).containsExactly(nextUser, nextReply);
    }

    // 串联两次工具调用的一组有 6 条，裁剪固定只删 4 条，队首落在第二次调用的 ToolResponseMessage 上。
    // 当前行为：裁剪后历史以孤儿 ToolResponseMessage 打头
    // 正确行为：按 tool_call 与 ToolResponseMessage 的配对关系整组删到下一条 UserMessage 为止
    // 生产代码 xiaozhi-ai/src/main/java/com/xiaozhi/ai/llm/memory/MessageWindowConversation.java:52-58
    @Test
    void chainedToolCallsLeaveOrphanToolResponseAtHead() {
        MessageWindowConversation conversation = conversation(2);
        ToolResponseMessage secondToolResponse = toolResponse("call-2", "getWeather");
        AssistantMessage finalReply = new AssistantMessage("北京今天晴");
        addAll(conversation,
                new UserMessage("我这儿天气怎么样"), toolCall("call-1", "getCity"),
                toolResponse("call-1", "getCity"), toolCall("call-2", "getWeather"),
                secondToolResponse, finalReply);

        conversation.messages();

        assertThat(conversation.rawMessages()).containsExactly(secondToolResponse, finalReply);
        assertThat(conversation.rawMessages().get(0)).isInstanceOf(ToolResponseMessage.class);
    }

    // 工具链还没收到最终回答时只有 [User, Assistant(toolCall), Tool] 三条，不满 4 条，
    // 裁剪退回简单组一次删 2 条，把 tool_call 删掉、只留下 ToolResponseMessage。
    // 这一状态在串联工具调用的中途取 messages() 时真实存在。
    // 当前行为：留下没有对应 tool_call 的孤儿 ToolResponseMessage
    // 正确行为：未完成的工具链要么整组删掉要么整组保留
    // 生产代码 xiaozhi-ai/src/main/java/com/xiaozhi/ai/llm/memory/MessageWindowConversation.java:54,59-65
    @Test
    void unfinishedToolChainIsCutInHalfWhenShorterThanFourMessages() {
        MessageWindowConversation conversation = conversation(1);
        ToolResponseMessage orphan = toolResponse("call-1", "getWeather");
        addAll(conversation,
                new UserMessage("今天天气怎么样"), toolCall("call-1", "getWeather"), orphan);

        conversation.messages();

        assertThat(conversation.rawMessages()).containsExactly(orphan);
    }

    @Test
    void maxMessagesOfOneKeepsOnlyTheLatestTurn() {
        MessageWindowConversation conversation = conversation(1);
        UserMessage lastUser = new UserMessage("第二问");
        AssistantMessage lastReply = new AssistantMessage("第二答");
        addAll(conversation, new UserMessage("第一问"), new AssistantMessage("第一答"), lastUser, lastReply);

        conversation.messages();

        assertThat(conversation.rawMessages()).containsExactly(lastUser, lastReply);
    }

    // 系统提示词由 messages() 每次现拼，不能被塞进历史
    @Test
    void systemMessageIsNotAddedToHistory() {
        MessageWindowConversation conversation = conversation(4);

        conversation.add(new SystemMessage("不该进历史"));

        assertThat(conversation.rawMessages()).isEmpty();
    }
}

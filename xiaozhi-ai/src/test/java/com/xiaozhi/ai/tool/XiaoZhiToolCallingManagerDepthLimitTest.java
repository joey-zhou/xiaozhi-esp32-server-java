package com.xiaozhi.ai.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class XiaoZhiToolCallingManagerDepthLimitTest {

    private static final String TOOL_NAME = "flaky_tool";

    private final XiaoZhiToolCallingManager manager = XiaoZhiToolCallingManager.builder().build();

    @Test
    void sixthRoundClearsToolsAndAppendsWrapUpInstruction() {
        ToolCallingChatOptions options = optionsWithFailingTool();
        List<Message> history = List.of(userMessage());

        for (int round = 1; round <= 5; round++) {
            history = nextRound(options, history);
        }

        assertThat(options.getToolCallbacks()).hasSize(1);
        assertThat(history.get(history.size() - 1)).isInstanceOf(ToolResponseMessage.class);

        history = nextRound(options, history);

        assertThat(options.getToolCallbacks()).isEmpty();
        assertThat(options.getToolNames()).isEmpty();
        assertThat(history.get(history.size() - 2)).isInstanceOf(ToolResponseMessage.class);
        Message lastMessage = history.get(history.size() - 1);
        assertThat(lastMessage).isInstanceOf(SystemMessage.class);
        assertThat(lastMessage.getText()).isEqualTo(XiaoZhiToolCallingManager.TOOL_DEPTH_LIMIT_INSTRUCTION);
    }

    @Test
    void toolChainsOfPreviousTurnsDoNotCountTowardDepth() {
        ToolCallingChatOptions options = optionsWithFailingTool();
        List<Message> history = new ArrayList<>();
        for (int previousTurn = 1; previousTurn <= 6; previousTurn++) {
            history.add(userMessage());
            history.add(toolCallAssistantMessage());
            history.add(toolResponseMessage());
        }
        history.add(userMessage());

        List<Message> afterRound = nextRound(options, history);

        assertThat(options.getToolCallbacks()).hasSize(1);
        assertThat(afterRound.get(afterRound.size() - 1)).isInstanceOf(ToolResponseMessage.class);
    }

    private List<Message> nextRound(ToolCallingChatOptions options, List<Message> history) {
        ToolExecutionResult result = manager.executeToolCalls(new Prompt(history, options), toolCallResponse());
        return result.conversationHistory();
    }

    private static ToolCallingChatOptions optionsWithFailingTool() {
        return ToolCallingChatOptions.builder()
                .toolCallbacks(List.of(failingToolCallback()))
                .build();
    }

    private static ToolCallback failingToolCallback() {
        return new ToolCallback() {

            @Override
            public ToolDefinition getToolDefinition() {
                return DefaultToolDefinition.builder()
                        .name(TOOL_NAME)
                        .description("总是失败的工具")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "调用超时，可以稍后重试";
            }
        };
    }

    private static UserMessage userMessage() {
        return new UserMessage("帮我看看客厅的灯");
    }

    private static ChatResponse toolCallResponse() {
        return ChatResponse.builder()
                .generations(List.of(new Generation(toolCallAssistantMessage())))
                .build();
    }

    private static AssistantMessage toolCallAssistantMessage() {
        return AssistantMessage.builder()
                .content("")
                .properties(Map.of())
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", TOOL_NAME, "{}")))
                .build();
    }

    private static ToolResponseMessage toolResponseMessage() {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", TOOL_NAME, "调用超时，可以稍后重试")))
                .build();
    }
}

package com.xiaozhi.ai.tool;

import com.xiaozhi.ai.tool.session.ToolSession;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具执行期间模型不出字，调用管理器要把执行进度登记下来，对话侧的首字与静默超时才能把这段时间让出去。
 */
class XiaoZhiToolCallingManagerActivityTest {

    private static final String TOOL_NAME = "slow_lookup";
    private static final Duration LIMIT = Duration.ofHours(1);

    private final ToolCallActivityRegistry activity = new ToolCallActivityRegistry();
    private final XiaoZhiToolCallingManager manager = XiaoZhiToolCallingManager.builder()
            .toolCallActivity(activity)
            .build();

    @Test
    void toolIsRegisteredAsRunningWhileItExecutes() {
        String key = ToolCallActivityRegistry.key("device-a", 42L);
        AtomicReference<Duration> seenDuringCall = new AtomicReference<>();

        manager.executeToolCalls(prompt(recordingTool(() -> seenDuringCall.set(activity.remainingQuiet(key, LIMIT)))),
                toolCallResponse());

        assertThat(seenDuringCall.get()).isEqualTo(LIMIT);
        assertThat(endedWithin(key, Duration.ofSeconds(2))).as("执行完要收回登记").isTrue();
    }

    /** 工具抛异常时也要把执行进度收回来，否则这一轮的沉默会被无限顺延 */
    @Test
    void failingToolStillEndsItsActivity() {
        String key = ToolCallActivityRegistry.key("device-a", 42L);

        manager.executeToolCalls(prompt(recordingTool(() -> {
            throw new IllegalStateException("设备没回应");
        })), toolCallResponse());

        assertThat(endedWithin(key, Duration.ofSeconds(2))).as("执行失败也要收回登记").isTrue();
    }

    /** 登记收回后剩余时间从结束那一刻开始减少；没收回就一直是整段 */
    private boolean endedWithin(String key, Duration deadline) {
        long start = System.nanoTime();
        while (System.nanoTime() - start < deadline.toNanos()) {
            if (activity.remainingQuiet(key, LIMIT).compareTo(LIMIT) < 0) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    private static Prompt prompt(ToolCallback tool) {
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(List.of(tool))
                .toolContext(Map.of(ToolSession.CONTEXT_OWNER_ID, "device-a", ToolSession.CONTEXT_TURN_ID, 42L))
                .build();
        return new Prompt(List.of(new UserMessage("查一下")), options);
    }

    private static ToolCallback recordingTool(Runnable onCall) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return DefaultToolDefinition.builder()
                        .name(TOOL_NAME)
                        .description("慢查询")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                onCall.run();
                return "查到了";
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return call(toolInput);
            }
        };
    }

    private static ChatResponse toolCallResponse() {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .properties(Map.of())
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", TOOL_NAME, "{}")))
                .build();
        return ChatResponse.builder().generations(List.of(new Generation(assistantMessage))).build();
    }
}

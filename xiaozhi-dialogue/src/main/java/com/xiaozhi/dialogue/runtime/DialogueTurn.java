package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import lombok.Builder;
import lombok.Value;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.Assert;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 表示一次 Conversation 中已完成的一轮交互：
 * 一个 UserMessage，对应一个最终 AssistantMessage，以及这一轮产生的时序与工具调用信息。
 * <p>
 * 一轮内可能有多个工具调用链（顺序排列），由 {@code toolChains} 表达，例如：
 * <ol>
 *   <li>模型生成中途主动调用的真实工具（MCP/内置 Function）</li>
 * </ol>
 * 持久化时按顺序写入 sys_message，回放时按顺序还原。
 * <p>
 * 仅作为 Conversation 里的单轮结果对象；持久化转换由
 * {@link com.xiaozhi.dialogue.runtime.convert.DialogueTurnConverter} 负责。
 */
@Value
public class DialogueTurn {

    private UserMessage userMessage;
    private ChatResponse chatResponse;
    private Conversation conversation;
    private Instant userMessageCreatedAt;
    private Instant assistantMessageCreatedAt;
    private List<DialogueContext.ToolCallInfo> toolCallDetails;
    private Path userSpeechPath;

    /**
     * 一轮内按时间顺序排列的工具调用链（可能为空）
     */
    private List<ToolChainPair> toolChains;

    private final AssistantMessage assistantMessage;
    private final Duration timeToFirstToken;

    @Builder
    public DialogueTurn(
            UserMessage userMessage,
            ChatResponse chatResponse,
            Conversation conversation,
            Path userSpeechPath,
            Instant userMessageCreatedAt,
            Instant assistantMessageCreatedAt,
            List<DialogueContext.ToolCallInfo> toolCallDetails,
            List<ToolChainPair> toolChains) {
        Assert.notNull(userMessage, "用户消息对象不应该为NULL！");
        Assert.notNull(chatResponse, "大语言模型的响应对象不应该为NULL！");
        Assert.notNull(conversation, "会话对象不应该为NULL！");
        Assert.notNull(userMessageCreatedAt, "用户消息创建时间对象不应该为NULL！");
        Assert.notNull(assistantMessageCreatedAt, "模型响应创建时间对象不应该为NULL！");
        this.userMessage = userMessage;
        this.chatResponse = chatResponse;
        this.conversation = conversation;
        this.userSpeechPath = userSpeechPath;
        this.timeToFirstToken = Duration.between(userMessageCreatedAt, assistantMessageCreatedAt);
        this.userMessageCreatedAt = userMessageCreatedAt.truncatedTo(ChronoUnit.SECONDS);
        this.assistantMessageCreatedAt = assistantMessageCreatedAt.truncatedTo(ChronoUnit.SECONDS);
        this.toolCallDetails = toolCallDetails != null ? toolCallDetails : List.of();
        this.toolChains = toolChains != null ? toolChains : List.of();
        this.assistantMessage = chatResponse.getResult().getOutput();
    }

    public void injectInstants() {
        MessageTimeMetadata.setTimeMillis(userMessage, userMessageCreatedAt);
        MessageTimeMetadata.setTimeMillis(assistantMessage, assistantMessageCreatedAt);
    }
}

package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import lombok.Builder;
import lombok.Value;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 表示一次 Conversation 中的一轮交互：
 * 一个 UserMessage，对应一个最终 AssistantMessage，以及这一轮产生的时序与工具调用信息。
 * <p>
 * 被打断的轮次也会生成 DialogueTurn：助手消息只到用户听到的位置；
 * 一个字都没播出就被打断时 assistantMessage 为 null，只剩用户消息与已完成的工具链。
 * <p>
 * 一轮内可能有多个工具调用链（顺序排列），由 {@code toolChains} 表达，
 * 即模型生成中途主动调用的真实工具（MCP/内置 Function）。
 * 持久化时按顺序写入 sys_message，回放时按顺序还原。
 * <p>
 * 仅作为 Conversation 里的单轮结果对象；持久化转换由
 * {@link com.xiaozhi.dialogue.runtime.convert.DialogueTurnConverter} 负责。
 */
@Value
public class DialogueTurn {

    UserMessage userMessage;
    /** 本轮最终助手消息，一个字都没播出就被打断时为 null */
    AssistantMessage assistantMessage;
    /** LLM 用量，被打断时可能为 null */
    Usage usage;
    Conversation conversation;
    Instant userMessageCreatedAt;
    /** 助手消息创建时间（首 token 时刻），assistantMessage 为 null 时也为 null */
    Instant assistantMessageCreatedAt;
    List<DialogueContext.ToolCallInfo> toolCallDetails;
    /** 用户音频持久化路径（本地相对路径或云存储完整 URL）。用原始字符串，避免 Path 破坏 URL。 */
    String userSpeechStoredPath;
    /** 用户音频时长（秒），在保存音频时用本地文件算好，避免此处重复读文件（云端已删本地文件）。 */
    Double sttDuration;
    /**
     * 一轮内按时间顺序排列的工具调用链（可能为空）
     */
    List<ToolChainPair> toolChains;
    /** 首 token 延迟，没有助手消息时为 null */
    Duration timeToFirstToken;
    /** 本轮是否被用户打断 */
    boolean interrupted;

    @Builder
    public DialogueTurn(
            UserMessage userMessage,
            AssistantMessage assistantMessage,
            Usage usage,
            Conversation conversation,
            String userSpeechStoredPath,
            Double sttDuration,
            Instant userMessageCreatedAt,
            Instant assistantMessageCreatedAt,
            List<DialogueContext.ToolCallInfo> toolCallDetails,
            List<ToolChainPair> toolChains,
            boolean interrupted) {
        Assert.notNull(userMessage, "用户消息对象不应该为NULL！");
        Assert.notNull(conversation, "会话对象不应该为NULL！");
        Assert.notNull(userMessageCreatedAt, "用户消息创建时间对象不应该为NULL！");
        if (assistantMessage != null) {
            Assert.notNull(assistantMessageCreatedAt, "模型响应创建时间对象不应该为NULL！");
        }
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
        this.usage = usage;
        this.conversation = conversation;
        this.userSpeechStoredPath = userSpeechStoredPath;
        this.sttDuration = sttDuration;
        this.userMessageCreatedAt = userMessageCreatedAt.truncatedTo(ChronoUnit.SECONDS);
        this.assistantMessageCreatedAt = assistantMessageCreatedAt != null
                ? assistantMessageCreatedAt.truncatedTo(ChronoUnit.SECONDS) : null;
        this.timeToFirstToken = assistantMessageCreatedAt != null
                ? Duration.between(userMessageCreatedAt, assistantMessageCreatedAt) : null;
        this.toolCallDetails = toolCallDetails != null ? toolCallDetails : List.of();
        this.toolChains = toolChains != null ? toolChains : List.of();
        this.interrupted = interrupted;
    }

    public void injectInstants() {
        MessageTimeMetadata.setTimeMillis(userMessage, userMessageCreatedAt);
        if (assistantMessage != null) {
            MessageTimeMetadata.setTimeMillis(assistantMessage, assistantMessageCreatedAt);
        }
    }

    /**
     * 工具链消息的落库时间：有助手消息用助手时间，没有则用用户消息时间
     */
    public Instant toolChainCreatedAt() {
        return assistantMessageCreatedAt != null ? assistantMessageCreatedAt : userMessageCreatedAt;
    }
}

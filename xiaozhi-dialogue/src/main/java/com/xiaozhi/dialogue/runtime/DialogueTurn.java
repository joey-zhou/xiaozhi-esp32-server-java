package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import lombok.Builder;
import lombok.Value;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
 * 只是当前 Conversation 里的单轮结果对象；持久化转换由
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
    private AssistantMessage toolCallAssistantMessage;
    private ToolResponseMessage toolResponseMessage;

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
            AssistantMessage toolCallAssistantMessage,
            ToolResponseMessage toolResponseMessage) {
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
        this.toolCallAssistantMessage = toolCallAssistantMessage;
        this.toolResponseMessage = toolResponseMessage;
        this.assistantMessage = chatResponse.getResult().getOutput();
    }

    public void injectInstants() {
        MessageTimeMetadata.setTimeMillis(userMessage, userMessageCreatedAt);
        MessageTimeMetadata.setTimeMillis(assistantMessage, assistantMessageCreatedAt);
    }
}

package com.xiaozhi.ai.llm.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 将 Spring AI 的消息列表序列化为单一文本块，专供"二次喂给大模型做摘要"场景。
 * <p>
 * 渲染约定：
 * <ul>
 *   <li>TOOL 消息 → {@code TOOL:<text>}</li>
 *   <li>ASSISTANT 携带 tool_calls → {@code ASSISTANT:[tool_call:<name1>,<name2>...]}
 *       （不落文本内容，避免把未解析的 JSON 喂回摘要模型）</li>
 *   <li>其它 → {@code <TYPE>:<text>}</li>
 * </ul>
 * 原本此逻辑在 {@code SummaryConversation#summaryMessages}
 */
public final class MessageHistoryFormatter {

    private MessageHistoryFormatter() {}

    /**
     * 按上述约定把消息列表渲染成单一字符串，消息间用 {@link System#lineSeparator()} 分隔。
     */
    public static String format(List<Message> messages) {
        return messages.stream()
                .map(MessageHistoryFormatter::renderOne)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String renderOne(Message message) {
        if (message.getMessageType() == MessageType.TOOL) {
            return "TOOL:" + message.getText();
        }
        if (message.getMessageType() == MessageType.ASSISTANT
                && message instanceof AssistantMessage am
                && am.getToolCalls() != null && !am.getToolCalls().isEmpty()) {
            String toolNames = am.getToolCalls().stream()
                    .map(AssistantMessage.ToolCall::name)
                    .collect(Collectors.joining(","));
            return "ASSISTANT:[tool_call:" + toolNames + "]";
        }
        return message.getMessageType() + ":" + message.getText();
    }
}

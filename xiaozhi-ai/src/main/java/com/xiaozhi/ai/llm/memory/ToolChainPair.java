package com.xiaozhi.ai.llm.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * 一次工具调用的 request/response 对：
 * <ul>
 *   <li>{@code toolCallMessage}：AssistantMessage，带 toolCalls</li>
 *   <li>{@code toolResponseMessage}：ToolResponseMessage，工具执行结果</li>
 * </ul>
 */
public record ToolChainPair(AssistantMessage toolCallMessage, ToolResponseMessage toolResponseMessage) {
}

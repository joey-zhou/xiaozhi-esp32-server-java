package com.xiaozhi.dialogue.runtime;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * 一次工具调用的 request/response 对：
 * <ul>
 *   <li>{@code toolCallMessage}：AssistantMessage，带 toolCalls（模型请求或装饰器伪造）</li>
 *   <li>{@code toolResponseMessage}：ToolResponseMessage，工具执行结果</li>
 * </ul>
 * <p>
 * 一轮 DialogueTurn 里可能有 0~N 个 pair，按时间顺序排列。持久化时按顺序写入 sys_message。
 */
public record ToolChainPair(AssistantMessage toolCallMessage, ToolResponseMessage toolResponseMessage) {
}

package com.xiaozhi.ai.llm.tool;

import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * @param returnDirect 调用工具后直接返回工具调用结果，不再调用大语言模型
 * @param disturbed 调用此工具的特定语言指令会污染对话上下文，标识从Conversation里剔除触发此工具调用的UserMessage。
 */
public record XiaozhiToolMetadata(boolean returnDirect,boolean disturbed) implements ToolMetadata {
}

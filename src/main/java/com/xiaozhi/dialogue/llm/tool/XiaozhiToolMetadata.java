package com.xiaozhi.dialogue.llm.tool;

import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * TODO rollback 改用 disturbed 更合适
 * @param returnDirect 调用工具后直接返回工具调用结果，不再调用大语言模型
 * @param rollback 调用此工具的特定语言指令会污染对话上下文，标识从Conversation里剔除触发此工具调用的UserMessage。
 */
public record XiaozhiToolMetadata(boolean returnDirect,boolean rollback) implements ToolMetadata {

}

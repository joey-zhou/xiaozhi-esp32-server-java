package com.xiaozhi.ai.llm.memory;

/**
 * 运行时上下文，每次构建 Prompt 时传入。
 * 与 Conversation 的身份属性（ownerId/roleId/sessionId）不同，
 * 这些字段在会话期间可能变化（设备搬迁等）。
 *
 * <p>会话期内<b>稳定</b>的字段放在此处注入 System Prompt（如 location）；
 * 而是由 {@link UserMessageAssembler} 拼接到各条 UserMessage 的文本前缀，
 * 避免 System Prompt 每轮变动导致前缀 KV cache 失效。
 *
 * @param location 设备位置 / Web 端 IP 定位 / null
 */
public record ConversationContext(
    String location
) {
    public static final ConversationContext EMPTY = new ConversationContext(null);
}

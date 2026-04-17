package com.xiaozhi.ai.llm.memory;

/**
 * 运行时上下文，每次构建 Prompt 时传入。
 * 与 Conversation 的身份属性（ownerId/roleId/sessionId）不同，
 * 这些字段在会话期间可能变化（设备搬迁、声纹识别结果等）。
 *
 * @param location 设备位置 / Web 端 IP 定位 / null
 */
public record ConversationContext(
    String location
) {
    public static final ConversationContext EMPTY = new ConversationContext(null);
}

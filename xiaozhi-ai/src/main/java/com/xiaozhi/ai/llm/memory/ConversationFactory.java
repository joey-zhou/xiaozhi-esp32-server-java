package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.RoleBO;

public interface ConversationFactory {
    /**
     * 设备对话：历史与摘要按 ownerId + roleId 跨会话延续。
     *
     * @param ownerId   设备 ID
     * @param userId    用户ID
     * @param role      角色
     * @param sessionId 会话ID
     * @return 会话
     */
    Conversation initConversation(String ownerId, Integer userId, RoleBO role, String sessionId);

    /**
     * Web 对话：历史与摘要按 sessionId 隔离，新会话为空，续接会拉到该会话的历史。
     *
     * @param ownerId   Web 聊天参与者标识
     * @param userId    用户ID
     * @param role      角色
     * @param sessionId 会话ID
     * @return 会话
     */
    Conversation initSessionConversation(String ownerId, Integer userId, RoleBO role, String sessionId);
}

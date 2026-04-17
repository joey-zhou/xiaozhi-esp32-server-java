package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.RoleBO;

public interface ConversationFactory {
    /**
     * 不同的ChatMemory实现类，可以有不同的处理策略，可以初始化不同的Conversation子类。
     *
     * @param ownerId   聊天参与者标识（设备场景: deviceId, Web 场景: userId）
     * @param userId    用户ID
     * @param role      角色
     * @param sessionId 会话ID
     * @return 会话
     */
    Conversation initConversation(String ownerId, Integer userId, RoleBO role, String sessionId);
}

package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.RoleBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 根据角色的 memoryType 构造对应的 Conversation。
 * <p>
 * Conversation 只负责消息容器 / 窗口策略 / 摘要策略。
 */
@Primary
@Service
@Slf4j
public class DefaultConversationFactory implements ConversationFactory {

    @Value("${conversation.max-messages:16}")
    private int maxMessages;

    @Autowired
    private ChatMemory chatMemory;
    @Autowired
    private SummaryConversationFactory summaryConversationFactory;

    @Override
    public Conversation initConversation(String ownerId, Integer userId, RoleBO role, String sessionId) {
        return switch (role.getMemoryType()) {
            case "summary" -> summaryConversationFactory.initConversation(ownerId, userId, role, sessionId);
            case "window" -> MessageWindowConversation.builder().chatMemory(chatMemory)
                    .maxMessages(maxMessages)
                    .ownerId(ownerId)
                    .roleId(role.getRoleId())
                    .roleDesc(role.getRoleDesc())
                    .userId(userId)
                    .sessionId(sessionId)
                    .build();
            default -> {
                log.warn("系统目前不支持这类未知的记忆类型：{} ，将启用默认的MessageWindowConversation", role.getMemoryType());
                yield MessageWindowConversation.builder().chatMemory(chatMemory)
                    .maxMessages(maxMessages)
                    .ownerId(ownerId)
                    .roleId(role.getRoleId())
                    .roleDesc(role.getRoleDesc())
                    .userId(userId)
                    .sessionId(sessionId)
                    .build();
            }
        };
    }
}

package com.xiaozhi.message.service;

import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.ConversationBO;

import java.util.Collection;
import java.util.List;

/**
 * Web 聊天会话（sys_conversation）：侧栏列表、标题与归属。
 */
public interface ConversationService {

    /** 当前用户的会话，按最后对话时间倒序，带角色名；roleId 为空时不限角色 */
    PageResult<ConversationBO> page(Integer userId, Integer roleId, int pageNo, int pageSize);

    ConversationBO get(String sessionId);

    /**
     * 会话归属的唯一判定：不存在抛 ResourceNotFoundException，不归属 userId 抛 UnauthorizedException。
     * 网页聊天是个人会话，管理员也不放行：续接别人的会话等于替别人说话。
     */
    void checkAccess(String sessionId, Integer userId);

    /** 会话第一轮对话时建档，标题取这句话的开头；已经建过则不动 */
    void create(String sessionId, Integer userId, Integer roleId, String firstMessage);

    /** 刷新最后对话时间 */
    void touch(String sessionId);

    /** 归属按 {@link #checkAccess} 判定 */
    void rename(Integer userId, String sessionId, String title);

    /** 删除当前用户名下的这些会话及其聊天记录与摘要，不归属的跳过，返回实际删掉的会话 ID */
    List<String> delete(Integer userId, Collection<String> sessionIds);
}

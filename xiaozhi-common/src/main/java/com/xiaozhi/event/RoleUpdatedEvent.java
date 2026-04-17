package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;
import lombok.Getter;

/**
 * 角色配置变更事件（更新或删除）。
 * 由 RoleRepositoryImpl.save() / delete() 发布，供对话会话层刷新 Persona 缓存。
 */
@Getter
public class RoleUpdatedEvent extends AbstractDomainEvent {

    private final Integer roleId;

    public RoleUpdatedEvent(Object source, Integer roleId) {
        super(source);
        this.roleId = roleId;
    }
}

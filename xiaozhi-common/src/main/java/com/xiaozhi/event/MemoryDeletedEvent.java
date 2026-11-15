package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;
import lombok.Getter;

/**
 * 某个记忆归属（设备或网页聊天身份）在某个角色下的摘要被删了。
 * 在线会话开会话时装进提示词的摘要是快照，不丢掉的话已删的内容还会接着被说出来、被压缩写回。
 */
@Getter
public class MemoryDeletedEvent extends AbstractDomainEvent {

    /** 设备 ID，或网页聊天的 {@code web:<userId>} */
    private final String ownerId;

    private final Integer roleId;

    public MemoryDeletedEvent(Object source, String ownerId, Integer roleId) {
        super(source);
        this.ownerId = ownerId;
        this.roleId = roleId;
    }
}

package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;
import lombok.Getter;

/**
 * Session 关闭事件
 */
@Getter
public class ChatSessionClosedEvent extends AbstractDomainEvent {

    private final String sessionId;
    private final String deviceId;

    public ChatSessionClosedEvent(Object source, String sessionId, String deviceId) {
        super(source);
        this.sessionId = sessionId;
        this.deviceId = deviceId;
    }
}

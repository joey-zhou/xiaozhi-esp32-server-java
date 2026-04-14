package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;

/**
 * Session 关闭事件
 */
public class ChatSessionClosedEvent extends AbstractDomainEvent {

    private final String sessionId;
    private final String deviceId;

    public ChatSessionClosedEvent(Object source, String sessionId, String deviceId) {
        super(source);
        this.sessionId = sessionId;
        this.deviceId = deviceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getDeviceId() {
        return deviceId;
    }
}

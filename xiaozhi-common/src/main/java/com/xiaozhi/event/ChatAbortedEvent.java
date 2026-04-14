package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;
import org.springframework.util.StringUtils;

/**
 * 设备端（客户端）发起打断的事件
 */
public class ChatAbortedEvent extends AbstractDomainEvent {

    private final String sessionId;
    private final String deviceId;
    private final String reason;

    public ChatAbortedEvent(Object source, String sessionId, String deviceId, String reason) {
        super(source);
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.reason = StringUtils.hasText(reason) ? reason : "设备端打断";
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getReason() {
        return reason;
    }
}

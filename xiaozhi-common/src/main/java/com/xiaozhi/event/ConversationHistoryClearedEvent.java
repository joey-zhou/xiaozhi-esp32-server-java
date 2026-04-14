package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;

/**
 * 对话历史清除事件，通知会话层通过 Redis 广播清除跨实例对话历史
 */
public class ConversationHistoryClearedEvent extends AbstractDomainEvent {

    private final String deviceId;

    public ConversationHistoryClearedEvent(Object source, String deviceId) {
        super(source);
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }
}

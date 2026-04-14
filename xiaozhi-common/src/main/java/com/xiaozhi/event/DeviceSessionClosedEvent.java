package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;

/**
 * 设备会话关闭事件（设备删除或重新激活时）。
 * 由 DeviceRepositoryImpl 在 delete() 或检测到 SESSION_CLOSED 信号时发布，
 * 触发跨实例广播关闭对应设备的 WebSocket 会话。
 */
public class DeviceSessionClosedEvent extends AbstractDomainEvent {

    private final String deviceId;

    public DeviceSessionClosedEvent(Object source, String deviceId) {
        super(source);
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }
}

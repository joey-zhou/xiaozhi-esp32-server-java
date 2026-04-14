package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;

/**
 * 设备绑定角色变更事件。
 * 由 DeviceRepositoryImpl.save() 在检测到 ROLE_CHANGED 信号时发布，
 * 触发跨实例广播使 Persona 重建。
 */
public class DeviceRoleChangedEvent extends AbstractDomainEvent {

    private final String deviceId;

    public DeviceRoleChangedEvent(Object source, String deviceId) {
        super(source);
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }
}

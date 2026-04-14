package com.xiaozhi.event;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.domain.AbstractDomainEvent;

/**
 * 设备信息变更事件，通知会话层同步设备信息
 */
public class DeviceUpdatedEvent extends AbstractDomainEvent {

    private final DeviceBO device;

    public DeviceUpdatedEvent(Object source, DeviceBO device) {
        super(source);
        this.device = device;
    }

    public DeviceBO getDevice() {
        return device;
    }
}

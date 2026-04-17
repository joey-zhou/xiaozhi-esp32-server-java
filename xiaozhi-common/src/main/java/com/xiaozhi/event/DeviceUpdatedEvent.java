package com.xiaozhi.event;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.domain.AbstractDomainEvent;
import lombok.Getter;

/**
 * 设备信息变更事件，通知会话层同步设备信息
 */
@Getter
public class DeviceUpdatedEvent extends AbstractDomainEvent {

    private final DeviceBO device;

    public DeviceUpdatedEvent(Object source, DeviceBO device) {
        super(source);
        this.device = device;
    }
}

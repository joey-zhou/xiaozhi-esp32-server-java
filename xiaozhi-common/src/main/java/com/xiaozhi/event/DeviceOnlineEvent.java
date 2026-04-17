package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;
import lombok.Getter;

/**
 * 设备重新上线/变为待机状态事件
 * 用于触发OTA升级结果的即时检查
 */
@Getter
public class DeviceOnlineEvent extends AbstractDomainEvent {
    private final String deviceId;

    public DeviceOnlineEvent(Object source, String deviceId) {
        super(source);
        this.deviceId = deviceId;
    }
}

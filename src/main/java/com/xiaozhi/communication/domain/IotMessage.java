package com.xiaozhi.communication.domain;

import com.xiaozhi.communication.domain.iot.IotDescriptor;
import com.xiaozhi.communication.domain.iot.IotState;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data

public final class IotMessage extends Message {
    public IotMessage() {
        super("iot");
    }

    private boolean update;
    private String sessionId;
    private List<IotState> states;
    private List<IotDescriptor> descriptors;

    // 显式添加 getter 方法
    public boolean isUpdate() {
        return update;
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<IotState> getStates() {
        return states;
    }

    public List<IotDescriptor> getDescriptors() {
        return descriptors;
    }
}

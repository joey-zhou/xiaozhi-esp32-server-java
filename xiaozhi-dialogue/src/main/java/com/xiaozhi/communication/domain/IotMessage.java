package com.xiaozhi.communication.domain;

import com.xiaozhi.communication.domain.iot.IotDescriptor;
import com.xiaozhi.communication.domain.iot.IotState;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public final class IotMessage extends Message {
    public IotMessage() {
        super("iot");
    }

    private boolean update;
    private String sessionId;
    private List<IotState> states;
    private List<IotDescriptor> descriptors;
}

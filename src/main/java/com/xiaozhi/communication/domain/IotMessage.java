package com.xiaozhi.communication.domain;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public final class IotMessage extends Message {
    public IotMessage() {
        super("iot");
    }

    private List<IotDescriptor> descriptors;
    private List<IotState> states;

    public static class IotDescriptor {
        // properties
        // private List<IotMethod> methods;
    }

    public static class IotState {

    }
}

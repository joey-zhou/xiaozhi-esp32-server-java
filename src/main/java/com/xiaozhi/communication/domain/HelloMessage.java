package com.xiaozhi.communication.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data

public final class HelloMessage extends Message {
    public HelloMessage() {
        super("hello");
    }

    private HelloFeatures features;
    private AudioParams audioParams;
}

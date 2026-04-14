package com.xiaozhi.communication.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public final class HelloMessage extends Message {
    public HelloMessage() {
        super("hello");
    }

    private HelloFeatures features;
    private AudioParams audioParams;
}

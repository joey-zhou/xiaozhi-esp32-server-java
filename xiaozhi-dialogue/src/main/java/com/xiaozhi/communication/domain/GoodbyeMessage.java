package com.xiaozhi.communication.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public final class GoodbyeMessage extends Message {
    public GoodbyeMessage() {
        super("goodbye");
    }
}

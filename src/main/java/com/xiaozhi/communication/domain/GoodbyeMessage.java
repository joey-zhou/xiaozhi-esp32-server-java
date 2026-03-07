package com.xiaozhi.communication.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data

public final class GoodbyeMessage extends Message {
    public GoodbyeMessage() {
        super("goodbye");
    }
}

package com.xiaozhi.communication.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public final class UnknownMessage extends Message {
    public UnknownMessage() {
        super("unknown");
    }
}

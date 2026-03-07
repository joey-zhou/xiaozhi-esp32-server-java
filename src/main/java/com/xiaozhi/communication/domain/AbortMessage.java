package com.xiaozhi.communication.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data

public final class AbortMessage extends Message {
    public AbortMessage() {
        super("abort");
    }

    private String reason;

    // 显式添加 getter 方法
    public String getReason() {
        return reason;
    }
}

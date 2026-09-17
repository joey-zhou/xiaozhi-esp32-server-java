package com.xiaozhi.communication.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户端保活报文。
 * 服务端只靠收到它重置会话空闲计时，不做任何业务处理、不回应答。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public final class PingMessage extends Message {
    public PingMessage() {
        super("ping");
    }
}

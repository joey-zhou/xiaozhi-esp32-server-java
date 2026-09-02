package com.xiaozhi.communication.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class HelloMessage extends Message {
    public HelloMessage() {
        super("hello");
    }

    /**
     * 设备声明的二进制帧版本(1/2/3)，缺省按 v1 裸帧
     */
    private Integer version;
    private HelloFeatures features;
    private AudioParams audioParams;
}

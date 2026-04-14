package com.xiaozhi.communication.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = HelloMessage.class, name = "hello"),
        @JsonSubTypes.Type(value = DeviceMcpMessage.class, name = "mcp"),
        @JsonSubTypes.Type(value = ListenMessage.class, name = "listen"),
        @JsonSubTypes.Type(value = IotMessage.class, name = "iot"),
        @JsonSubTypes.Type(value = AbortMessage.class, name = "abort"),
        @JsonSubTypes.Type(value = GoodbyeMessage.class, name = "goodbye"),
        @JsonSubTypes.Type(value = UnknownMessage.class, name = "unknown")
})
public sealed abstract class Message
        permits AbortMessage, GoodbyeMessage, HelloMessage, IotMessage, ListenMessage, DeviceMcpMessage, UnknownMessage {

    public Message() {
        this.type = "unknown";
    }

    public Message(String type) {
        this.type = type;
    }

    @NotNull(message = "消息类型不能为空")
    protected String type;

}

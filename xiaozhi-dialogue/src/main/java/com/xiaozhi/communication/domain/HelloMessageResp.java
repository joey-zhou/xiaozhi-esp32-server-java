package com.xiaozhi.communication.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class HelloMessageResp {
    private String type = "hello";
    private String transport;
    private String sessionId;
    private AudioParams audioParams;
}

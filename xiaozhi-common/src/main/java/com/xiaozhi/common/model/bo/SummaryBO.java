package com.xiaozhi.common.model.bo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.Instant;

@Data
@Accessors(chain = true)
public class SummaryBO implements Serializable {

    private String deviceId;
    private Integer roleId;

    private Instant lastMessageTimestamp;

    private String summary;
    private Integer promptTokens = 0;
    private Integer completionTokens = 0;

    private Instant createTime;
}

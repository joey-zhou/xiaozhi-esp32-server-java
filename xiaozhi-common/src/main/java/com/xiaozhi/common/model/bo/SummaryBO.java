package com.xiaozhi.common.model.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.Instant;

@Data
@Accessors(chain = true)
public class SummaryBO implements Serializable {

    private String deviceId;
    private Integer roleId;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Instant lastMessageTimestamp;

    private String summary;
    private Integer promptTokens = 0;
    private Integer completionTokens = 0;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Instant createTime;

    @JsonProperty
    public Long getId() {
        return createTime != null ? createTime.toEpochMilli() : null;
    }
}

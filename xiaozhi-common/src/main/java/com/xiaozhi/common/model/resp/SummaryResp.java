package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class SummaryResp {

    private String deviceId;
    private Integer roleId;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Instant lastMessageTimestamp;

    private String summary;
    private Integer promptTokens = 0;
    private Integer completionTokens = 0;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Instant createTime;

    /** 以 createTime 的毫秒时间戳作 id，删除接口按它反查。 */
    @JsonProperty
    public Long getId() {
        return createTime != null ? createTime.toEpochMilli() : null;
    }
}

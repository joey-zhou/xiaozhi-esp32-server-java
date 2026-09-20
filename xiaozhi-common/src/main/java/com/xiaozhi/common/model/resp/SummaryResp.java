package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaozhi.utils.DateUtils;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SummaryResp {

    private String deviceId;
    private String deviceName;
    private Integer roleId;
    private String roleName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastMessageTimestamp;

    private String summary;
    private Integer promptTokens = 0;
    private Integer completionTokens = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 以 createTime 的毫秒时间戳作 id，删除接口按它反查。 */
    @JsonProperty
    public Long getId() {
        return createTime != null ? DateUtils.toInstant(createTime).toEpochMilli() : null;
    }
}

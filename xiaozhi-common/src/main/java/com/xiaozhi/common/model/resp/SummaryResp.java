package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class SummaryResp {

    private String deviceId;
    private Integer roleId;

    // Instant 是绝对时刻，格式化成 yyyy-MM-dd HH:mm:ss 必须指定时区，去掉 timezone 会直接抛
    // UnsupportedTemporalTypeException。这里的 GMT+8 与全局的 Asia/Shanghai 假设一致
    // （见 docker-compose 的 serverTimezone），与 MessageResp 的 LocalDateTime 渲染结果对齐；
    // 要彻底消掉这个硬编码，得先统一应用时钟与数据库时钟的来源。
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

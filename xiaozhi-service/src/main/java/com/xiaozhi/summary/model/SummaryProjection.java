package com.xiaozhi.summary.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 摘要分页结果集，字段名与 SummaryMapper.xml 的列别名逐字一致。
 * 时间列按库里的墙上时间读成 LocalDateTime，再由转换器按应用时区换成时刻：
 * 直接读成 Instant 会由驱动按连接串里的时区换算，与应用时区不同时整体偏移。
 */
@Data
public class SummaryProjection {

    private String deviceId;
    private String deviceName;
    private Integer roleId;
    private String roleName;
    private String sessionId;
    private LocalDateTime lastMessageTimestamp;
    private String summary;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime createTime;
}

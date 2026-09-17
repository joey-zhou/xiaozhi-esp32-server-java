package com.xiaozhi.common.model.bo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.Instant;

@Data
@Accessors(chain = true)
public class SummaryBO implements Serializable {

    private String deviceId;
    /** 分页查询时由 SQL 关联设备表带出，其它路径为 null */
    private String deviceName;
    private Integer roleId;
    /** 分页查询时由 SQL 关联角色表带出，其它路径为 null */
    private String roleName;
    /** Web 会话的摘要按会话隔离，设备端为空 */
    private String sessionId;

    private Instant lastMessageTimestamp;

    private String summary;
    private Integer promptTokens = 0;
    private Integer completionTokens = 0;

    private Instant createTime;
}

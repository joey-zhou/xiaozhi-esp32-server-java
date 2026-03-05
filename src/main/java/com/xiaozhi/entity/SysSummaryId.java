package com.xiaozhi.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.Instant;

/**
 * SysSummary 联合主键类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SysSummaryId implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备 ID
     */
    private String deviceId;

    /**
     * 角色 ID
     */
    private Integer roleId;

    /**
     * 最后一条消息时间戳
     */
    private Instant lastMessageTimestamp;
}

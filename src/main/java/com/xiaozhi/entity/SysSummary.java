package com.xiaozhi.entity;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Summary 表达一组聊天消息的总结摘要与重要信息提炼。
 *
 * @author Able
 */
@Data
@Accessors(chain = true)
@Entity
@Table(name = "sys_summary")
@IdClass(SysSummaryId.class)
public class SysSummary implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备 ID，必填，不为空字符串。联合主键第一个字段。
     */
    @Id
    @Column(nullable = false, length = 255)
    @Schema(description = "设备 ID")
    private String deviceId = "";

    /**
     * 角色 ID，必填，大于零的整数。联合主键第二个字段。
     */
    @Id
    @Column(nullable = false)
    @Schema(description = "角色 ID")
    private Integer roleId;

    /**
     * 最后一条消息的创建时间戳。联合主键第三个字段。
     */
    @Id
    @Column(nullable = false)
    @Schema(description = "最后一条消息时间戳")
    private Instant lastMessageTimestamp;

    /**
     * 摘要内容。提炼出来的重要信息。
     */
    @Column(columnDefinition = "text")
    @Schema(description = "摘要内容")
    private String summary;

    /**
     * 摘要动作本身消耗的 promptTokens。
     */
    @Schema(description = "消耗的 prompt tokens 数量")
    private Integer promptTokens = 0;

    /**
     * 摘要动作本身消耗的 completionTokens。
     */
    @Schema(description = "消耗的 completion tokens 数量")
    private Integer completionTokens = 0;

    /**
     * 创建日期
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Column
    @Schema(description = "创建时间")
    private Instant createTime;
}

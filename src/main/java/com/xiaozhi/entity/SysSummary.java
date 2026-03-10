package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

/**
 * Summary 表达一组聊天消息的总结摘要与重要信息提炼。
 * 可以理解为中期记忆而非长期记忆，当时间拉长，device-role 自诞生以来的所有对话消息的内容太多，难以进行良好的摘要。
 * 所以只能对一段时期的一定规模的消息列表进行摘要。
 * 在 sys_summary 表增加最后一条消息的 timestamp，表达已 sumary 的消息。
 * 目前只是记录一组消息的最后一条的时间戳，用来表达 summary 的消息范围。
 * 后续如果有找到需要这组消息起点的场景。也可修改代码记录起始的那一条，可能更好更完整。
 *
 * @author Able
 */
@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "sys_summary")
@IdClass(SysSummaryId.class)
@DynamicUpdate
@DynamicInsert
public class SysSummary implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备 ID，必填，不为空字符串。联合索引第一个字段。
     */
    @Id
    @Column(length = 255, nullable = false)
    @Schema(description = "设备 ID")
    private String deviceId = "";

    /**
     * 角色 ID，必填，大于零的整数。联合索引第二个字段。
     */
    @Id
    @Column(nullable = false)
    @Schema(description = "角色 ID")
    private Integer roleId;

    /**
     * 最后一条消息的创建时间戳。联合索引第三个字段。
     * 用于关联 messages 表中的消息。
     * 只有当 lastMessage.promptTokens + lastMessage.completionTokens > completionTokens 时，
     * 摘要动作才有较多的节省 tokens 成本意义。
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
    @Column
    @Schema(description = "消耗的 prompt tokens 数量")
    private Integer promptTokens = 0;

    /**
     * 摘要动作本身消耗的 completionTokens。
     */
    @Column
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

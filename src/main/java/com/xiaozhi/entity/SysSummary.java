package com.xiaozhi.entity;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Summary表达一组聊天消息的总结摘要与重要信息提炼。
 * 可以理解为中期记忆而非长期记忆，当时间拉长，device-role自诞生以来的所有对话消息的内容太多，难以进行良好的摘要。
 * 所以只能对一段时期的一定规模的消息列表进行摘要。
 * 在sys_summary表增加最后一条消息的 timestamp，表达已sumary的消息。
 * 目前只是记录一组消息的最后一条的时间戳，用来表达summary的消息范围。
 * 后续如果有找到需要这组消息起点的场景。也可修改代码记录起始的那一条，可能更好更完整。
 *
 * @author Able
 */
@Data
@Accessors(chain = true)
public class SysSummary implements java.io.Serializable {

    /**
     * 设备ID,必填，不为空字符串。联合索引第一个字段。
     */
    @NotBlank
    private String deviceId = "";

    /**
     * 角色ID,必填，大于零的整数。联合索引第二个字段。
     */
    @Positive
    private int roleId = -1;

    // 未来有需要时，可以增加firstMessageTimestamp

    /**
     * 最后一条消息的创建时间戳。联合索引第三个字段。
     * 用于关联messages表中的消息。
     * 只有当lastMessage.promptTokens + lastMessage.completionTokens > completionTokens 时，
     * 摘要动作才有较多的节省tokens成本意义。
     */
    @NotNull
    private java.time.Instant lastMessageTimestamp ;

    /**
     * 摘要内容。提炼出来的重要信息。
     */
    private String summary;
    /**
     * 摘要动作本身消耗的promptTokens。
     * 用于计算摘要功能可以节省多少的tokens。
     */
    @PositiveOrZero
    private Integer promptTokens = 0;

    /**
     * 摘要动作本身消耗的completionTokens。
     * 用于计算摘要功能可以节省多少的tokens。
     */
    @PositiveOrZero
    private Integer completionTokens = 0;
    /**
     * 创建日期
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Instant  createTime;

    @JsonProperty
    private Long getId() {
        // 将createTime转换为毫秒数
        return createTime != null ? createTime.toEpochMilli() : null;
    }
}
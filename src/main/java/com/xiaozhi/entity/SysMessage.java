package com.xiaozhi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 聊天记录表
 *
 * @author Joey
 *
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "消息信息")
@Entity
@Table(name = "sys_message")
@DynamicUpdate
@DynamicInsert
public class SysMessage extends Base<SysMessage> {
    public static final String MESSAGE_TYPE_NORMAL = "NORMAL";
    public static final String MESSAGE_TYPE_FUNCTION_CALL = "FUNCTION_CALL";

    /**
     * 消息 ID，主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "消息 ID")
    private Integer messageId;

    /**
     * 设备 ID
     */
    @Column(nullable = false, length = 30)
    @Schema(description = "设备 ID")
    private String deviceId;

    /**
     * 会话 ID
     */
    @Column(nullable = false, length = 100)
    @Schema(description = "会话 ID")
    private String sessionId;

    /**
     * 消息发送方：user-用户，assistant-人工智能
     */
    @Column(nullable = false, length = 20)
    @Schema(description = "消息发送方：user-用户，assistant-人工智能")
    private String sender;

    /**
     * 角色 ID
     */
    @Column(nullable = false)
    @Schema(description = "角色 ID")
    private Integer roleId;

    /**
     * 消息内容
     */
    @Column(columnDefinition = "text")
    @Schema(description = "消息内容")
    private String message;

    /**
     * 语音文件路径
     */
    @Column(length = 100)
    @Schema(description = "语音文件路径")
    private String audioPath;

    /**
     * 语音状态
     */
    @Column(length = 1)
    @Schema(description = "语音状态")
    private String state;

    /**
     * 消息类型：NORMAL-普通消息，FUNCTION_CALL-函数调用消息
     */
    @Column(length = 20)
    @Schema(description = "消息类型：NORMAL-普通消息，FUNCTION_CALL-函数调用消息")
    private String messageType;

    /**
     * 工具调用详情（JSON 数组），记录本轮对话中调用的工具名称、参数和执行结果
     */
    @Column(columnDefinition = "text")
    @Schema(description = "工具调用详情 JSON，包含 name/arguments/result")
    private String toolCalls;

    /**
     * 角色名称（非数据库字段）
     */
    @Transient
    @Schema(description = "角色名称")
    private String roleName;

    /**
     * 设备名称（非数据库字段）
     */
    @Transient
    @Schema(description = "设备名称")
    private String deviceName;
}

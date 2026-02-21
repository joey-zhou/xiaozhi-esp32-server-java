package com.xiaozhi.entity;

import com.xiaozhi.utils.AudioUtils;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.sql.Date;
import java.text.SimpleDateFormat;

/**
 * 聊天记录表
 * 
 * @author Joey
 * 
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "消息信息")
public class SysMessage extends Base<SysMessage> {
    /**
     * 消息类型 - 普通消息
     */
    public static final String MESSAGE_TYPE_NORMAL = "NORMAL";
    /**
     * 消息类型 - 函数调用消息
     */
    public static final String MESSAGE_TYPE_FUNCTION_CALL = "FUNCTION_CALL";

    @Schema(description = "消息ID")
    private Integer messageId;

    @Schema(description = "设备ID")
    private String deviceId;

    /**
     * 消息发送方：user-用户，assistant-人工智能
     * 后续考虑：重命名为speaker是否更好？
     */
    @Schema(description = "消息发送方：user-用户，assistant-人工智能")
    private String sender;

    /**
     * 消息内容
     */
    @Schema(description = "消息内容")
    private String message;
    

    /**
     * 语音文件路径
     */
    @Schema(description = "语音文件路径")
    private String audioPath;

    /**
     * 语音状态
     * 
     */
    @Schema(description = "语音状态")
    private String state;

    /**
     * 消息类型: NORMAL-普通消息，FUNCTION_CALL-函数调用消息
     *
     */
    @Schema(description = "消息类型: NORMAL-普通消息，FUNCTION_CALL-函数调用消息")
    private String messageType = "NORMAL";

    /**
     * 工具调用详情（JSON数组），记录本轮对话中调用的工具名称、参数和执行结果
     */
    @Schema(description = "工具调用详情JSON，包含name/arguments/result")
    private String toolCalls;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "角色ID")
    private Integer roleId;

    //辅助字段，不对应数据库表
    @Schema(description = "角色名称")
    private String roleName;
    @Schema(description = "设备名称")
    private String deviceName;

}
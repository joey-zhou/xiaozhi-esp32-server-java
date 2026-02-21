package com.xiaozhi.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 消息信息响应DTO
 *
 * @author Joey
 */
@Data
@Schema(description = "消息信息")
public class MessageDTO {

    @Schema(description = "消息ID", example = "1")
    private Integer messageId;

    @Schema(description = "设备ID", example = "ESP32_001")
    private String deviceId;

    @Schema(description = "设备名称", example = "客厅小智")
    private String deviceName;

    @Schema(description = "消息发送方：user-用户，ai-人工智能", example = "user", allowableValues = {"user", "ai"})
    private String sender;

    @Schema(description = "消息内容", example = "今天天气怎么样？")
    private String message;

    @Schema(description = "语音文件路径", example = "/audio/message_123456.mp3")
    private String audioPath;

    @Schema(description = "消息类型: NORMAL-普通消息，FUNCTION_CALL-函数调用消息", example = "NORMAL", allowableValues = {"NORMAL", "FUNCTION_CALL"})
    private String messageType;

    @Schema(description = "工具调用详情JSON，包含name/arguments/result")
    private String toolCalls;

    @Schema(description = "会话ID", example = "session_123456")
    private String sessionId;

    @Schema(description = "角色ID", example = "1")
    private Integer roleId;

    @Schema(description = "角色名称", example = "默认助手")
    private String roleName;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间", example = "2024-01-01 00:00:00")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间", example = "2024-12-01 10:30:00")
    private Date updateTime;
}

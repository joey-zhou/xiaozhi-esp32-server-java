package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "消息信息")
public class MessageResp {

    @Schema(description = "消息ID")
    private Integer messageId;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "发送方")
    private String sender;

    @Schema(description = "消息内容")
    private String message;

    @Schema(description = "语音文件路径")
    private String audioPath;

    @Schema(description = "消息状态")
    private String state;

    @Schema(description = "消息类型")
    private String messageType;

    @Schema(description = "工具调用详情")
    private String toolCalls;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "角色ID")
    private Integer roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

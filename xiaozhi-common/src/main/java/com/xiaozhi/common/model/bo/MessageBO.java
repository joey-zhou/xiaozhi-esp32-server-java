package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MessageBO {

    public static final String STATE_ENABLED = "1";
    public static final String STATE_DELETED = "0";
    public static final String MESSAGE_TYPE_NORMAL = "NORMAL";
    public static final String MESSAGE_TYPE_TOOL_CALL = "TOOL_CALL";
    public static final String MESSAGE_TYPE_TOOL_RESPONSE = "TOOL_RESPONSE";

    private Integer messageId;
    private Integer userId;
    private String deviceId;
    private String sender;
    private String message;
    private Integer tokens;
    private BigDecimal sttDuration;
    private BigDecimal ttsDuration;
    private Long ttfsTime;
    private Integer responseTime;
    private LocalDate statDate;
    private String audioPath;
    private String state;
    private String messageType;
    private String toolCalls;
    private String sessionId;
    private Integer roleId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

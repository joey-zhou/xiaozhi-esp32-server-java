package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MessageBO {

    public static final String STATE_ENABLED = "1";
    public static final String STATE_DELETED = "0";

    public static final String SENDER_USER = "user";
    public static final String SENDER_ASSISTANT = "assistant";
    public static final String SENDER_TOOL = "tool";

    public static final String MESSAGE_TYPE_NORMAL = "NORMAL";
    public static final String MESSAGE_TYPE_TOOL_CALL = "TOOL_CALL";
    public static final String MESSAGE_TYPE_TOOL_RESPONSE = "TOOL_RESPONSE";

    public static final String SOURCE_WEB = "web";
    public static final String SOURCE_DEVICE = "device";

    private Long messageId;
    private Integer userId;
    private String deviceId;
    private String sender;
    private String message;
    private LocalDate statDate;
    private String audioPath;
    private String state;
    private String messageType;
    private String toolCalls;
    private String sessionId;
    private String source;
    private Integer roleId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

package com.xiaozhi.message.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 消息分页结果集，字段名与 MessageMapper.xml 的列别名逐字一致。 */
@Data
public class MessageProjection {

    private Long messageId;
    private String deviceId;
    private String deviceName;
    private String sender;
    private String message;
    private String audioPath;
    private String state;
    private String messageType;
    private String toolCalls;
    private String sessionId;
    private String source;
    private Integer roleId;
    private String roleName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

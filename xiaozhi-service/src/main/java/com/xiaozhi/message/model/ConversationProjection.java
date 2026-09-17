package com.xiaozhi.message.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 会话分页结果集，字段名与 ConversationMapper.xml 的列别名逐字一致。 */
@Data
public class ConversationProjection {

    private String sessionId;
    private Integer roleId;
    private String roleName;
    private String title;
    private LocalDateTime updateTime;
}

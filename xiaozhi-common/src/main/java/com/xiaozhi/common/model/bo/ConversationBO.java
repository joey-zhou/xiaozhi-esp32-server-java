package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Web 聊天会话。
 */
@Data
public class ConversationBO {

    private String sessionId;
    private Integer userId;
    private Integer roleId;
    /** 分页查询时由 SQL 关联角色表带出，其它路径为 null */
    private String roleName;
    private String title;
    private LocalDateTime createTime;
    /** 最后一次对话的时间 */
    private LocalDateTime updateTime;
}

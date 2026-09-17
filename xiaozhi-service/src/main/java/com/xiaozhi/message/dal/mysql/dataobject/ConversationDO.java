package com.xiaozhi.message.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Web 聊天会话。时间字段不走自动填充：updateTime 只在对话时刷新，改标题不动它。
 */
@Data
@TableName("sys_conversation")
public class ConversationDO {

    @TableId(value = "sessionId", type = IdType.INPUT)
    private String sessionId;

    private Integer userId;

    private Integer roleId;

    private String title;

    private LocalDateTime createTime;

    /** 最后一次对话的时间，列表按它倒序 */
    private LocalDateTime updateTime;
}

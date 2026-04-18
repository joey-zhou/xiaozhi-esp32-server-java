package com.xiaozhi.message.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.model.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message")
public class MessageDO extends BaseDO {

    @TableId(value = "messageId", type = IdType.AUTO)
    private Long messageId;

    private Integer userId;
    private String deviceId;
    private String sender;
    private String message;
    /**
     * UserMessage 附加元数据的 JSON 序列化字符串（speaker/emotion 等）。
     * 仅 sender=user 的行可能有值，其他为 null。
     */
    private String metadata;
    private LocalDate statDate;
    private String audioPath;
    private String state;
    private String messageType;
    private String toolCalls;
    private String sessionId;
    private String source;
    private Integer roleId;
}

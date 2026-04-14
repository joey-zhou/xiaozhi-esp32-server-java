package com.xiaozhi.message.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.model.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message")
public class MessageDO extends BaseDO {

    @TableId(value = "messageId", type = IdType.AUTO)
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
}

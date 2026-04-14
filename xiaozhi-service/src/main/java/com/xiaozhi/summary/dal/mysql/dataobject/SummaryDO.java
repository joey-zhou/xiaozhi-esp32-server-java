package com.xiaozhi.summary.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_summary")
public class SummaryDO {

    private String deviceId;
    private Integer roleId;
    private LocalDateTime lastMessageTimestamp;
    private String summary;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime createTime;
}

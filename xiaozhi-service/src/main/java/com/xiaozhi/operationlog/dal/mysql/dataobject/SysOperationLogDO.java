package com.xiaozhi.operationlog.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class SysOperationLogDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer userId;
    private String ip;
    private String module;
    private String operation;
    private String method;
    private String url;
    private String handler;
    private String params;
    private Boolean success;
    private String errorMsg;
    private Integer costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

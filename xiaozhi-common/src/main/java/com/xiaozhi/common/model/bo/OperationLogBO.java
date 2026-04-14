package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志 BO（对应 sys_operation_log 表）。
 */
@Data
public class OperationLogBO {

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
    private LocalDateTime createTime;
}

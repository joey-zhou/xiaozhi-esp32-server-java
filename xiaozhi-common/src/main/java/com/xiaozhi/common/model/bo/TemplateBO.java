package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateBO {

    public static final String STATE_ENABLED = "1";
    public static final String STATE_DISABLED = "0";
    public static final String DEFAULT_YES = "1";
    public static final String DEFAULT_NO = "0";

    private Integer templateId;
    private Integer userId;
    private String templateName;
    private String templateDesc;
    private String templateContent;
    private String category;
    private String isDefault;
    private String state;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

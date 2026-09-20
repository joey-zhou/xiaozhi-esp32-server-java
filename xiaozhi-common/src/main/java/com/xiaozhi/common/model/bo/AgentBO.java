package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentBO {

    private Integer configId;
    private Integer userId;
    private String configName;
    private String configDesc;
    private String configType;
    private String modelType;
    private String provider;
    private String appId;
    private String apiUrl;
    private String state;
    private String isDefault;
    private String agentName;
    private String botId;
    private String agentDesc;
    private String iconUrl;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

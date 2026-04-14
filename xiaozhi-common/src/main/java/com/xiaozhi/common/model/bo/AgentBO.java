package com.xiaozhi.common.model.bo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
public class AgentBO {

    private Integer configId;
    private Integer userId;
    private String deviceId;
    private Integer roleId;
    private String configName;
    private String configDesc;
    private String configType;
    private String modelType;
    private String provider;
    private String appId;
    private String apiUrl;
    private String state;
    private String isDefault;
    private Integer agentId;
    private String agentName;
    private String botId;
    private String agentDesc;
    private String iconUrl;
    private Date publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

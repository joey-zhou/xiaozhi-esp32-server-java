package com.xiaozhi.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 智能体实体类
 * 
 * @author Joey
 */
public class SysAgent extends SysConfig {

    /** 智能体ID */
    private Integer agentId;

    /** 智能体名称 */
    private String agentName;

    /** 平台智能体空间ID */
    private String spaceId;

    /** 平台智能体ID */
    private String botId;

    /** 智能体描述 */
    private String agentDesc;

    /** 图标URL */
    private String iconUrl;

    /** 发布时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;

    public Integer getAgentId() {
        return agentId;
    }

    public SysAgent setAgentId(Integer agentId) {
        this.agentId = agentId;
        return this;
    }

    public String getAgentName() {
        return agentName;
    }

    public SysAgent setAgentName(String agentName) {
        this.agentName = agentName;
        return this;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public SysAgent setSpaceId(String spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public String getBotId() {
        return botId;
    }

    public SysAgent setBotId(String botId) {
        this.botId = botId;
        return this;
    }

    public String getAgentDesc() {
        return agentDesc;
    }

    public SysAgent setAgentDesc(String agentDesc) {
        this.agentDesc = agentDesc;
        return this;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public SysAgent setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
        return this;
    }

    public Date getPublishTime() {
        return publishTime;
    }

    public SysAgent setPublishTime(Date publishTime) {
        this.publishTime = publishTime;
        return this;
    }
}
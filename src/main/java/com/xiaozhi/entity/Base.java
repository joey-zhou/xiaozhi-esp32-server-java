package com.xiaozhi.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 基础实体类
 * 
 * @author Joey
 * 
 */
@JsonIgnoreProperties({ "start", "limit", "userId", "startTime", "endTime" })
public class Base {

    /**
     * 分页
     */
    private Integer start;

    private Integer limit;

    /**
     * 创建日期
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 开始/结束时间筛选
     */
    private String startTime;

    private String endTime;

    public Integer getStart() {
        return start;
    }

    public Base setStart(Integer start) {
        this.start = start;
        return this;
    }

    public Integer getLimit() {
        return limit;
    }

    public Base setLimit(Integer limit) {
        this.limit = limit;
        return this;
    }

    public Integer getUserId() {
        return userId;
    }

    public Base setUserId(Integer userId) {
        this.userId = userId;
        return this;
    }

    public String getStartTime() {
        return startTime;
    }

    public Base setStartTime(String startTime) {
        this.startTime = startTime;
        return this;
    }

    public String getEndTime() {
        return endTime;
    }

    public Base setEndTime(String endTime) {
        this.endTime = endTime;
        return this;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public Base setCreateTime(Date createTime) {
        this.createTime = createTime;
        return this;
    }

}
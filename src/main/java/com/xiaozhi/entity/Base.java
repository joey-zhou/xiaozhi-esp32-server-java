package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * 基础实体类，所有实体类的父类
 *
 * @author Joey
 * @param <T> 泛型参数，用于支持链式调用
 *
 */
@Getter
@Accessors(chain = true)
@JsonIgnoreProperties({ "start", "limit", "startTime", "endTime" })
@Schema(description = "基础信息")
public class Base<T extends Base<T>> implements java.io.Serializable {
    /**
     * 创建日期
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    protected Date createTime;

    /**
     * 更新日期
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    protected Date updateTime;

    /**
     * 用户 ID
     */
    @Schema(description = "用户 ID")
    protected Integer userId;

    /**
     * 查询开始时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "查询开始时间")
    private Date startTime;

    /**
     * 查询结束时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "查询结束时间")
    private Date endTime;

    @SuppressWarnings("unchecked")
    public T setUserId(Integer userId) {
        this.userId = userId;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setStartTime(Date startTime) {
        this.startTime = startTime;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setEndTime(Date endTime) {
        this.endTime = endTime;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setCreateTime(Date createTime) {
        this.createTime = createTime;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
        return (T) this;
    }
}

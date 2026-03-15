package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 基础实体类，所有实体类的父类
 *
 * @author Joey
 * @param <T> 泛型参数，用于支持链式调用
 *
 */
@Getter
@JsonIgnoreProperties({ "start", "limit" })
@Schema(description = "基础信息")
public class Base<T extends Base<T>> implements java.io.Serializable {
    /**
     * 创建日期
     */
    @Column(name = "create_time", columnDefinition = "TIMESTAMP")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    protected LocalDateTime createTime;

    /**
     * 更新日期
     */
    @Column(name = "update_time", columnDefinition = "TIMESTAMP")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    protected LocalDateTime updateTime;

    /**
     * 用户 ID
     */
    @Schema(description = "用户 ID")
    protected Integer userId;




    @SuppressWarnings("unchecked")
    public T setUserId(Integer userId) {
        this.userId = userId;
        return (T) this;
    }



    @SuppressWarnings("unchecked")
    public T setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
        return (T) this;
    }

}

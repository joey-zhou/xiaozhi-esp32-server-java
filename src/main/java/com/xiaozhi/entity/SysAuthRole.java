package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 用户角色配置（管理员、用户）
 *
 * @author Joey
 *
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "角色信息")
@Entity
@Table(name = "sys_auth_role")
public class SysAuthRole {

    /**
     * 角色 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "角色 ID")
    private Integer roleId;

    /**
     * 角色名称
     */
    @Column(nullable = false, length = 100)
    @Schema(description = "角色名称")
    private String roleName;

    /**
     * 角色标识
     */
    @Column(nullable = false, length = 100)
    @Schema(description = "角色标识")
    private String roleKey;

    /**
     * 角色描述
     */
    @Column(length = 500)
    @Schema(description = "角色描述")
    private String description;

    /**
     * 状态 (1 正常 0 禁用)
     */
    @Column(length = 1)
    @Schema(description = "状态 (1 正常 0 禁用)")
    private String status;

    /**
     * 创建时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(updatable = false)
    @Schema(description = "创建时间")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private Date updateTime;

    /**
     * 用户 ID（非数据库字段）
     */
    @Transient
    @Schema(description = "用户 ID")
    private Integer userId;
}

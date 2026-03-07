package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

/**
 * 权限类
 *
 * @author Joey
 */
@Data
@Accessors(chain = true)
@Schema(description = "权限信息")
@Entity
@Table(name = "sys_permission")
public class SysPermission {

    /**
     * 权限 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "权限 ID")
    private Integer permissionId;

    /**
     * 父权限 ID
     */
    @Schema(description = "父权限 ID")
    private Integer parentId;

    /**
     * 权限名称
     */
    @Column(nullable = false, length = 100)
    @Schema(description = "权限名称")
    private String name;

    /**
     * 权限标识
     */
    @Column(nullable = false, length = 100)
    @Schema(description = "权限标识")
    private String permissionKey;

    /**
     * 权限类型：menu-菜单，button-按钮，api-接口
     */
    @Column(nullable = false, length = 20)
    @Schema(description = "权限类型：menu-菜单，button-按钮，api-接口")
    private String permissionType;

    /**
     * 前端路由路径
     */
    @Column(length = 255)
    @Schema(description = "前端路由路径")
    private String path;

    /**
     * 前端组件路径
     */
    @Column(length = 255)
    @Schema(description = "前端组件路径")
    private String component;

    /**
     * 图标
     */
    @Column(length = 100)
    @Schema(description = "图标")
    private String icon;

    /**
     * 排序
     */
    @Schema(description = "排序")
    private Integer sort;

    /**
     * 是否可见 (1 可见 0 隐藏)
     */
    @Column(length = 1)
    @Schema(description = "是否可见 (1 可见 0 隐藏)")
    private String visible;

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
     * 子权限列表（非数据库字段）
     */
    @Transient
    @Schema(description = "子权限列表")
    private List<SysPermission> children;

}
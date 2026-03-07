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
 * 角色权限配置
 *
 * @author Joey
 *
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "角色权限配置")
@Entity
@Table(name = "sys_role_permission")
public class SysRolePermission {

    /**
     * 主键 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "主键 ID")
    private Integer id;

    /**
     * 角色 ID
     */
    @Column(nullable = false)
    @Schema(description = "角色 ID")
    private Integer roleId;

    /**
     * 权限 ID
     */
    @Column(nullable = false)
    @Schema(description = "权限 ID")
    private Integer permissionId;

    /**
     * 创建时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(updatable = false)
    @Schema(description = "创建时间")
    private Date createTime;
}

package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * MCP 工具过滤配置实体
 */
@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "sys_mcp_tool_exclude")
@DynamicUpdate
@DynamicInsert
public class SysMcpToolExclude {

    /**
     * 主键 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 过滤类型：global-全局过滤，role-角色过滤
     */
    @Column(nullable = false, length = 20)
    private String excludeType;

    /**
     * 绑定类型：mcp_server-MCP 服务器，mcp_endpoint-MCP 接入点
     */
    @Column(nullable = false, length = 20)
    private String bindType;

    /**
     * 绑定的 MCP 服务器代码或接入点标识
     */
    @Column(nullable = false, length = 100)
    private String bindCode;

    /**
     * 绑定键：roleId，全局过滤时为 0
     */
    @Column(nullable = false, length = 50)
    private String bindKey;

    /**
     * 要排除的工具函数名称列表，JSON 数组格式
     */
    @Column(nullable = false, columnDefinition = "text")
    private String excludeTools;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(nullable = false)
    private LocalDateTime updateTime;
}

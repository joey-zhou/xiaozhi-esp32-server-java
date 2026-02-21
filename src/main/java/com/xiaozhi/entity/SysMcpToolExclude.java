package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP工具过滤配置实体
 */
@Data
public class SysMcpToolExclude {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 过滤类型：global-全局过滤，role-角色过滤
     */
    private String excludeType;
    
    /**
     * 绑定类型：mcp_server-MCP服务器，mcp_endpoint-MCP接入点
     */
    private String bindType;
    
    /**
     * 绑定的MCP服务器代码或接入点标识
     */
    private String bindCode;
    
    /**
     * 绑定键：roleId，全局过滤时为0
     */
    private String bindKey;
    
    /**
     * 要排除的工具函数名称列表，JSON数组格式
     */
    private String excludeTools;
    
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}

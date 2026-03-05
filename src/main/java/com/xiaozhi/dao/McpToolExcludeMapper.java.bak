package com.xiaozhi.dao;

import com.xiaozhi.entity.SysMcpToolExclude;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MCP工具过滤配置 数据层
 */
@Mapper
public interface McpToolExcludeMapper {
    
    /**
     * 查询过滤配置列表
     */
    List<SysMcpToolExclude> query(SysMcpToolExclude sysMcpToolExclude);
    
    /**
     * 根据条件查询过滤配置
     */
    List<SysMcpToolExclude> selectByCondition(@Param("excludeType") String excludeType,
                                              @Param("bindType") String bindType,
                                              @Param("bindCode") String bindCode,
                                              @Param("bindKey") String bindKey);
    
    /**
     * 根据主键查询
     */
    SysMcpToolExclude selectById(Long id);
    
    /**
     * 新增过滤配置
     */
    int add(SysMcpToolExclude sysMcpToolExclude);
    
    /**
     * 更新过滤配置
     */
    int update(SysMcpToolExclude sysMcpToolExclude);
    
    /**
     * 根据主键删除
     */
    int delete(Long id);
    
    /**
     * 根据条件删除
     */
    int deleteByCondition(@Param("excludeType") String excludeType,
                         @Param("bindType") String bindType,
                         @Param("bindCode") String bindCode,
                         @Param("bindKey") String bindKey);
}

package com.xiaozhi.repository;

import com.xiaozhi.entity.SysMcpToolExclude;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MCP 工具过滤配置数据访问层
 */
@Repository
public interface SysMcpToolExcludeRepository extends JpaRepository<SysMcpToolExclude, Long>, JpaSpecificationExecutor<SysMcpToolExclude> {

    /**
     * 根据条件查询 MCP 工具过滤配置
     *
     * @param excludeType 过滤类型
     * @param bindType    绑定类型
     * @param bindCode    绑定代码
     * @param bindKey     绑定键
     * @return 配置列表
     */
    @Query(value = "SELECT * FROM sys_mcp_tool_exclude " +
            "WHERE exclude_type = :excludeType AND bind_type = :bindType " +
            "AND bind_code = :bindCode AND bind_key = :bindKey",
            nativeQuery = true)
    List<SysMcpToolExclude> findByCondition(
            @Param("excludeType") String excludeType,
            @Param("bindType") String bindType,
            @Param("bindCode") String bindCode,
            @Param("bindKey") String bindKey);

    /**
     * 根据条件查询 MCP 工具过滤配置
     */
    @Query(value = "SELECT * FROM sys_mcp_tool_exclude " +
            "WHERE exclude_type = :excludeType AND bind_type = :bindType " +
            "AND bind_code = :bindCode AND bind_key = :bindKey",
            nativeQuery = true)
    List<SysMcpToolExclude> selectByCondition(
            @Param("excludeType") String excludeType,
            @Param("bindType") String bindType,
            @Param("bindCode") String bindCode,
            @Param("bindKey") String bindKey);

    default void delete(Long id){
        deleteById(id);
    }

    default void update(SysMcpToolExclude config){
        save(config);
    }

    default void add(SysMcpToolExclude sysMcpToolExclude){
        save(sysMcpToolExclude);
    }
}

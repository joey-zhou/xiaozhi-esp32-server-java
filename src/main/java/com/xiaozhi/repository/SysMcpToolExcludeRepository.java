package com.xiaozhi.repository;

import com.xiaozhi.entity.SysMcpToolExclude;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MCP 工具过滤配置数据访问层
 */
@Repository
public interface SysMcpToolExcludeRepository extends JpaRepository<SysMcpToolExclude, Long>, JpaSpecificationExecutor<SysMcpToolExclude> {

    /**
     * 根据条件查询 MCP 工具过滤配置
     */
    @Query(value = "SELECT * FROM sys_mcp_tool_exclude " +
            "WHERE excludeType = :excludeType AND bindType = :bindType " +
            "AND bindCode = :bindCode AND bindKey = :bindKey",
            nativeQuery = true)
    List<SysMcpToolExclude> findByCondition(
            @Param("excludeType") String excludeType,
            @Param("bindType") String bindType,
            @Param("bindCode") String bindCode,
            @Param("bindKey") String bindKey);

    default List<SysMcpToolExclude> selectByCondition(
            @Param("excludeType") String excludeType,
            @Param("bindType") String bindType,
            @Param("bindCode") String bindCode,
            @Param("bindKey") String bindKey) {
        return findByCondition(excludeType, bindType, bindCode, bindKey);
    }

    default SysMcpToolExclude selectById(Long id) {
        return findById(id).orElse(null);
    }

    default int add(SysMcpToolExclude sysMcpToolExclude) {
        save(sysMcpToolExclude);
        return 1;
    }

    default int update(SysMcpToolExclude sysMcpToolExclude) {
        save(sysMcpToolExclude);
        return 1;
    }

    default int delete(Long id) {
        deleteById(id);
        return 1;
    }

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sys_mcp_tool_exclude " +
            "WHERE excludeType = :excludeType AND bindType = :bindType " +
            "AND bindCode = :bindCode AND bindKey = :bindKey",
            nativeQuery = true)
    int deleteByCondition(@Param("excludeType") String excludeType,
                          @Param("bindType") String bindType,
                          @Param("bindCode") String bindCode,
                          @Param("bindKey") String bindKey);
}

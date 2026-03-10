package com.xiaozhi.repository;

import com.xiaozhi.entity.SysSummary;
import com.xiaozhi.entity.SysSummaryId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 聊天消息摘要数据访问层 - Spring Data JPA Repository
 *
 * @author Able
 */
@Repository
public interface SysSummaryRepository extends JpaRepository<SysSummary, SysSummaryId>, JpaSpecificationExecutor<SysSummary> {

    /**
     * 查找最新的消息摘要
     *
     * @param deviceId 设备 ID
     * @param roleId   角色 ID
     * @return 摘要信息
     */
    @Query(value = "SELECT * FROM sys_summary " +
            "WHERE device_id = :deviceId AND role_id = :roleId " +
            "ORDER BY create_time DESC LIMIT 1",
            nativeQuery = true)
    SysSummary findLastSummary(@Param("deviceId") String deviceId, @Param("roleId") int roleId);

    /**
     * 根据设备 ID 和角色 ID 查询摘要列表 - 分页
     *
     * @param deviceId 设备 ID
     * @param roleId   角色 ID
     * @param pageable 分页参数
     * @return 摘要分页列表
     */
    @Query(value = "SELECT * FROM sys_summary " +
            "WHERE device_id = :deviceId AND role_id = :roleId " +
            "ORDER BY create_time DESC",
            countQuery = "SELECT COUNT(*) FROM sys_summary " +
            "WHERE device_id = :deviceId AND role_id = :roleId",
            nativeQuery = true)
    Page<SysSummary> findSummary(
            @Param("deviceId") String deviceId,
            @Param("roleId") int roleId,
            Pageable pageable);

    /**
     * 删除摘要
     *
     * @param roleId     角色 ID
     * @param deviceId   设备 ID
     * @param createTime 创建时间
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sys_summary " +
            "WHERE role_id = :roleId AND device_id = :deviceId AND create_time = :createTime",
            nativeQuery = true)
    int deleteSummary(@Param("roleId") int roleId, @Param("deviceId") String deviceId, @Param("createTime") Instant createTime);

    default void saveSummary(SysSummary summary) {
        save(summary);
    }

    default List<SysSummary> findSummary(SysSummary summary) {
        return findAll();
    }
}

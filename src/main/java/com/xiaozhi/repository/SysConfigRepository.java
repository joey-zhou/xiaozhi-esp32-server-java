package com.xiaozhi.repository;

import com.xiaozhi.entity.SysConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 配置数据访问层 - Spring Data JPA Repository
 *
 * @author Joey
 */
@Repository
public interface SysConfigRepository extends JpaRepository<SysConfig, Integer>, JpaSpecificationExecutor<SysConfig> {

    /**
     * 根据配置 ID 查询配置
     *
     * @param configId 配置 ID
     * @return 配置信息
     */
    @Query(value = "SELECT * FROM sys_config WHERE config_id = :configId", nativeQuery = true)
    Optional<SysConfig> findConfigById(@Param("configId") Integer configId);

    /**
     * 查询配置列表 - 分页
     *
     * @param userId     用户 ID（可选）
     * @param configType 配置类型（可选）
     * @param provider   服务提供商（可选）
     * @param state      状态（可选）
     * @param pageable   分页参数
     * @return 配置分页列表
     */
    @Query(value = "SELECT * FROM sys_config WHERE 1=1 " +
            "AND (:userId IS NULL OR user_id = :userId) " +
            "AND (:configType IS NULL OR :configType = '' OR config_type = :configType) " +
            "AND (:provider IS NULL OR :provider = '' OR provider = :provider) " +
            "AND (:state IS NULL OR :state = '' OR state = :state) " +
            "ORDER BY create_time DESC",
            countQuery = "SELECT COUNT(*) FROM sys_config WHERE 1=1 " +
            "AND (:userId IS NULL OR user_id = :userId) " +
            "AND (:configType IS NULL OR :configType = '' OR config_type = :configType) " +
            "AND (:provider IS NULL OR :provider = '' OR provider = :provider) " +
            "AND (:state IS NULL OR :state = '' OR state = :state)",
            nativeQuery = true)
    Page<SysConfig> findConfigs(
            @Param("userId") Integer userId,
            @Param("configType") String configType,
            @Param("provider") String provider,
            @Param("state") String state,
            Pageable pageable);

    /**
     * 重置默认配置
     *
     * @param userId     用户 ID
     * @param configType 配置类型
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE sys_config SET is_default = '0' WHERE user_id = :userId AND config_type = :configType", nativeQuery = true)
    int resetDefault(@Param("userId") Integer userId, @Param("configType") String configType);

    /**
     * 根据用户 ID 和配置类型查询默认配置
     *
     * @param userId     用户 ID
     * @param configType 配置类型
     * @return 默认配置
     */
    @Query(value = "SELECT * FROM sys_config WHERE user_id = :userId AND config_type = :configType AND is_default = '1' LIMIT 1", nativeQuery = true)
    Optional<SysConfig> findDefaultConfig(@Param("userId") Integer userId, @Param("configType") String configType);
}

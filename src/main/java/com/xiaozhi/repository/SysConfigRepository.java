package com.xiaozhi.repository;

import com.xiaozhi.entity.SysConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
     * 重置默认配置（支持 modelType）
     *
     * @param userId     用户 ID
     * @param configType 配置类型
     * @param modelType  模型类型（可选）
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE sys_config SET is_default = '0' WHERE user_id = :userId AND config_type = :configType" +
            " AND (:modelType IS NULL OR :modelType = '' OR model_type = :modelType)", nativeQuery = true)
    int resetDefault(@Param("userId") Integer userId,
                     @Param("configType") String configType,
                     @Param("modelType") String modelType);

    /**
     * 根据用户 ID 和配置类型查询默认配置
     *
     * @param userId     用户 ID
     * @param configType 配置类型
     * @return 默认配置
     */
    @Query(value = "SELECT * FROM sys_config WHERE user_id = :userId AND config_type = :configType AND is_default = '1' LIMIT 1", nativeQuery = true)
    Optional<SysConfig> findDefaultConfig(@Param("userId") Integer userId, @Param("configType") String configType);

    /**
     * 动态查询配置列表
     *
     * @param sysConfig 查询条件
     * @return 配置列表
     */
    default List<SysConfig> query(SysConfig sysConfig) {
        return query(sysConfig, null);
    }

    /**
     * 动态查询配置列表
     *
     * @param sysConfig 查询条件
     * @return 配置列表
     */
    default List<SysConfig> query(SysConfig sysConfig, Pageable pageable) {
        Specification<SysConfig> spec = (root, query, cb) -> {
            query.distinct(true);

            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            // state = '1'
            predicates.add(cb.equal(root.get("state"), "1"));

            // userId 条件
            if (sysConfig.getUserId() != null && sysConfig.getUserId() != 0) {
                predicates.add(cb.equal(root.get("userId"), sysConfig.getUserId()));
            }

            // isDefault 条件
            if (sysConfig.getIsDefault() != null && !sysConfig.getIsDefault().isEmpty()) {
                predicates.add(cb.equal(root.get("isDefault"), sysConfig.getIsDefault()));
            }

            // configType 条件
            if (sysConfig.getConfigType() != null && !sysConfig.getConfigType().isEmpty()) {
                predicates.add(cb.equal(root.get("configType"), sysConfig.getConfigType()));
            }

            // modelType 条件
            if (sysConfig.getModelType() != null && !sysConfig.getModelType().isEmpty()) {
                predicates.add(cb.equal(root.get("modelType"), sysConfig.getModelType()));
            }

            // provider 条件
            if (sysConfig.getProvider() != null && !sysConfig.getProvider().isEmpty()) {
                predicates.add(cb.equal(root.get("provider"), sysConfig.getProvider()));
            } else {
                predicates.add(cb.not(root.get("provider").in("coze", "dify", "xingchen")));
            }

            // configName 模糊查询
            if (sysConfig.getConfigName() != null && !sysConfig.getConfigName().isEmpty()) {
                predicates.add(cb.like(root.get("configName"), "%" + sysConfig.getConfigName() + "%"));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        if (pageable != null) {
            return findAll(spec, pageable).getContent();
        } else {
            return findAll(spec);
        }
    }

    default int add(SysConfig config) {
        save(config);
        return 1;
    }

    default int update(SysConfig config) {
        save(config);
        return 1;
    }

    default void resetDefault(SysConfig resetConfig) {
        if (resetConfig == null || resetConfig.getUserId() == null
                || resetConfig.getConfigType() == null || resetConfig.getConfigType().isEmpty()) {
            return;
        }
        resetDefault(resetConfig.getUserId(), resetConfig.getConfigType(), resetConfig.getModelType());
    }

    default SysConfig selectConfigById(Integer configId) {
        return findById(configId).orElse(null);
    }
}

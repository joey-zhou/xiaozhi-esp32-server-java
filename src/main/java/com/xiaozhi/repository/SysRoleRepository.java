package com.xiaozhi.repository;

import com.xiaozhi.entity.SysRole;
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
 * 角色数据访问层 - Spring Data JPA Repository
 *
 * @author Joey
 */
@Repository
public interface SysRoleRepository extends JpaRepository<SysRole, Integer>, JpaSpecificationExecutor<SysRole> {

    /**
     * 根据角色 ID 查询角色
     *
     * @param roleId 角色 ID
     * @return 角色信息
     */
    @Query(value = "SELECT * FROM sys_role WHERE role_id = :roleId", nativeQuery = true)
    Optional<SysRole> findRoleById(@Param("roleId") Integer roleId);

    /**
     * 查询角色列表 - 分页
     *
     * @param userId    用户 ID（可选）
     * @param state     状态（可选）
     * @param isDefault 是否默认（可选）
     * @param pageable  分页参数
     * @return 角色分页列表
     */
    @Query(value = "SELECT * FROM sys_role WHERE 1=1 " +
            "AND (:userId IS NULL OR user_id = :userId) " +
            "AND (:state IS NULL OR :state = '' OR state = :state) " +
            "AND (:isDefault IS NULL OR :isDefault = '' OR is_default = :isDefault) " +
            "ORDER BY create_time DESC",
            countQuery = "SELECT COUNT(*) FROM sys_role WHERE 1=1 " +
            "AND (:userId IS NULL OR user_id = :userId) " +
            "AND (:state IS NULL OR :state = '' OR state = :state) " +
            "AND (:isDefault IS NULL OR :isDefault = '' OR is_default = :isDefault)",
            nativeQuery = true)
    Page<SysRole> findRoles(
            @Param("userId") Integer userId,
            @Param("state") String state,
            @Param("isDefault") String isDefault,
            Pageable pageable);

    /**
     * 重置默认角色
     *
     * @param userId 用户 ID
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE sys_role SET is_default = '0' WHERE user_id = :userId", nativeQuery = true)
    int resetDefault(@Param("userId") Integer userId);

    /**
     * 根据角色 ID 删除角色
     *
     * @param roleId 角色 ID
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sys_role WHERE role_id = :roleId", nativeQuery = true)
    int deleteRoleById(@Param("roleId") Integer roleId);
}

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
    @Query(value = "SELECT * FROM sys_role WHERE roleId = :roleId", nativeQuery = true)
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
            "AND (:userId IS NULL OR userId = :userId) " +
            "AND (:state IS NULL OR :state = '' OR state = :state) " +
            "AND (:isDefault IS NULL OR :isDefault = '' OR isDefault = :isDefault) " +
            "ORDER BY createTime DESC",
            countQuery = "SELECT COUNT(*) FROM sys_role WHERE 1=1 " +
            "AND (:userId IS NULL OR userId = :userId) " +
            "AND (:state IS NULL OR :state = '' OR state = :state) " +
            "AND (:isDefault IS NULL OR :isDefault = '' OR isDefault = :isDefault)",
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
    @Query(value = "UPDATE sys_role SET isDefault = '0' WHERE userId = :userId", nativeQuery = true)
    int resetDefault(@Param("userId") Integer userId);

    /**
     * 根据角色 ID 删除角色
     *
     * @param roleId 角色 ID
     * @return 影响行数
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sys_role WHERE roleId = :roleId", nativeQuery = true)
    int deleteRoleById(@Param("roleId") Integer roleId);

    /**
     * 查询角色列表（带关联配置和设备统计）
     *
     * @param userId    用户 ID（可选）
     * @param roleId    角色 ID（可选）
     * @param roleName  角色名称（可选，模糊匹配）
     * @param isDefault 是否默认（可选）
     * @return 角色列表（包含关联的配置信息和设备总数）
     */
    @Query(value = "SELECT r.*, " +
            "tts_config.configName AS ttsConfigName, tts_config.provider AS ttsProvider, " +
            "model_config.configName AS modelConfigName, model_config.provider AS modelProvider, " +
            "stt_config.configName AS sttConfigName, stt_config.provider AS sttProvider, " +
            "(SELECT COUNT(*) FROM sys_device WHERE sys_device.roleId = r.roleId) AS totalDevice " +
            "FROM sys_role r " +
            "LEFT JOIN sys_config tts_config ON r.ttsId = tts_config.configId AND tts_config.configType = 'tts' " +
            "LEFT JOIN sys_config model_config ON r.modelId = model_config.configId AND model_config.configType = 'llm' " +
            "LEFT JOIN sys_config stt_config ON r.ttsId = stt_config.configId AND stt_config.configType = 'stt' " +
            "WHERE r.state = '1' " +
            "AND (:userId IS NULL OR :userId = '' OR r.userId = :userId) " +
            "AND (:roleId IS NULL OR :roleId = '' OR r.userId = :roleId) " +
            "AND (:roleName IS NULL OR :roleName = '' OR r.roleName LIKE CONCAT('%', :roleName, '%')) " +
            "AND (:isDefault IS NULL OR :isDefault = '' OR r.isDefault = :isDefault)",
            nativeQuery = true)
    List<SysRole> queryRoles(
            @Param("userId") Integer userId,
            @Param("roleId") Integer roleId,
            @Param("roleName") String roleName,
            @Param("isDefault") String isDefault);

    default List<SysRole> query(SysRole queryRole) {
        return queryRoles(
                queryRole.getUserId(),
                queryRole.getRoleId(),
                queryRole.getRoleName(),
                queryRole.getIsDefault()
        );
    }

    default SysRole selectRoleById(Integer roleId) {
        return findRoleById(roleId).orElse(null);
    }

    default int update(SysRole role) {
        save(role);
        return 1;
    }

    default int resetDefault(SysRole role) {
        return resetDefault(role.getUserId());
    }

    default int add(SysRole role) {
        save(role);
        return 1;
    }
}

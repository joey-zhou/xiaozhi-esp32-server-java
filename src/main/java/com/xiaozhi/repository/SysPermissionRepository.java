package com.xiaozhi.repository;

import com.xiaozhi.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 权限数据访问层 - Spring Data JPA Repository
 *
 * @author Joey
 */
@Repository
public interface SysPermissionRepository extends JpaRepository<SysPermission, Integer>, JpaSpecificationExecutor<SysPermission> {

    /**
     * 根据权限 ID 查询权限
     */
    @Query(value = "SELECT * FROM sys_permission WHERE permissionId = :permissionId", nativeQuery = true)
    SysPermission findByPermissionId(@Param("permissionId") Integer permissionId);

    /**
     * 根据权限标识查询权限
     */
    @Query(value = "SELECT * FROM sys_permission WHERE permissionKey = :permissionKey", nativeQuery = true)
    SysPermission findByPermissionKey(@Param("permissionKey") String permissionKey);

    /**
     * 根据用户 ID 查询权限列表
     */
    @Query(value = "SELECT DISTINCT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.permissionId = rp.permissionId " +
            "INNER JOIN sys_user u ON u.roleId = rp.roleId " +
            "WHERE u.userId = :userId", nativeQuery = true)
    List<SysPermission> findByUserId(@Param("userId") Integer userId);

    /**
     * 根据权限类型查询权限
     */
    @Query(value = "SELECT * FROM sys_permission WHERE permissionType = :permissionType", nativeQuery = true)
    List<SysPermission> findByPermissionType(@Param("permissionType") String permissionType);

    /**
     * 根据父权限 ID 查询子权限
     */
    @Query(value = "SELECT * FROM sys_permission WHERE parentId = :parentId ORDER BY sort ASC", nativeQuery = true)
    List<SysPermission> findByParentIdOrderBySortAsc(@Param("parentId") Integer parentId);

    /**
     * 根据角色 ID 查询权限
     */
    @Query(value = "SELECT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.permissionId = rp.permissionId " +
            "WHERE rp.roleId = :roleId", nativeQuery = true)
    List<SysPermission> findPermissionsByRoleId(@Param("roleId") Integer roleId);

    /**
     * 根据角色 ID 和状态查询权限
     */
    @Query(value = "SELECT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.permissionId = rp.permissionId " +
            "WHERE rp.roleId = :roleId AND p.status = :status " +
            "ORDER BY p.sort ASC", nativeQuery = true)
    List<SysPermission> findPermissionsByRoleIdAndStatus(@Param("roleId") Integer roleId, @Param("status") String status);

    /**
     * 根据用户 ID 查询权限
     */
    @Query(value = "SELECT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.permissionId = rp.permissionId " +
            "INNER JOIN sys_auth_role ar ON rp.roleId = ar.roleId " +
            "INNER JOIN sys_user u ON u.roleId = ar.roleId " +
            "WHERE u.userId = :userId", nativeQuery = true)
    List<SysPermission> findPermissionsByUserId(@Param("userId") Integer userId);

    default List<SysPermission> selectAll() {
        return findAll();
    }

    default SysPermission selectById(Integer permissionId) {
        return findByPermissionId(permissionId);
    }

    default List<SysPermission> selectByType(String permissionType) {
        return findByPermissionType(permissionType);
    }

    default List<SysPermission> selectByParentId(Integer parentId) {
        return findByParentIdOrderBySortAsc(parentId);
    }

    default List<SysPermission> selectByRoleId(Integer roleId) {
        return findPermissionsByRoleIdAndStatus(roleId, "1");
    }

    default List<SysPermission> selectByUserId(Integer userId) {
        return findByUserId(userId);
    }

    default int add(SysPermission permission) {
        save(permission);
        return 1;
    }

    default int update(SysPermission permission) {
        save(permission);
        return 1;
    }

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sys_permission WHERE permissionId = :permissionId", nativeQuery = true)
    int delete(@Param("permissionId") Integer permissionId);
}

package com.xiaozhi.repository;

import com.xiaozhi.entity.SysRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色权限关联数据访问层 - Spring Data JPA Repository
 *
 * @author Joey
 */
@Repository
public interface SysRolePermissionRepository extends JpaRepository<SysRolePermission, Integer>, JpaSpecificationExecutor<SysRolePermission> {

    /**
     * 根据角色 ID 查询角色权限关联
     */
    @Query(value = "SELECT * FROM sys_role_permission WHERE role_id = :roleId", nativeQuery = true)
    List<SysRolePermission> findByRoleId(@Param("roleId") Integer roleId);

    /**
     * 根据权限 ID 查询角色权限关联
     */
    @Query(value = "SELECT * FROM sys_role_permission WHERE permission_id = :permissionId", nativeQuery = true)
    List<SysRolePermission> findByPermissionId(@Param("permissionId") Integer permissionId);

    /**
     * 根据角色 ID 和权限 ID 查询角色权限关联
     */
    @Query(value = "SELECT * FROM sys_role_permission WHERE role_id = :roleId AND permission_id = :permissionId", nativeQuery = true)
    SysRolePermission findByRoleIdAndPermissionId(@Param("roleId") Integer roleId, @Param("permissionId") Integer permissionId);

    /**
     * 根据角色 ID 删除角色权限关联
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sys_role_permission WHERE role_id = :roleId", nativeQuery = true)
    int deleteByRoleId(@Param("roleId") Integer roleId);

    /**
     * 根据权限 ID 删除角色权限关联
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM sys_role_permission WHERE permission_id = :permissionId", nativeQuery = true)
    int deleteByPermissionId(@Param("permissionId") Integer permissionId);

    default int batchAdd(List<SysRolePermission> rolePermissions){
        return saveAll(rolePermissions).size();
    }
}

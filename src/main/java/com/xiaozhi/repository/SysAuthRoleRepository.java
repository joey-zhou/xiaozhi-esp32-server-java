package com.xiaozhi.repository;

import com.xiaozhi.entity.SysAuthRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色 Repository 接口
 *
 * @author Joey
 */
@Repository
public interface SysAuthRoleRepository extends JpaRepository<SysAuthRole, Integer>, JpaSpecificationExecutor<SysAuthRole> {

    /**
     * 根据 ID 查询角色
     */
    @Query(value = "SELECT * FROM sys_auth_role WHERE roleId = :roleId", nativeQuery = true)
    SysAuthRole selectById(@Param("roleId") Integer roleId);

    /**
     * 根据 ID 查询角色
     */
    @Query(value = "SELECT * FROM sys_auth_role WHERE roleId = :roleId", nativeQuery = true)
    SysAuthRole findRoleById(@Param("roleId") Integer roleId);

    /**
     * 根据用户 ID 查询角色
     */
    @Query(value = "SELECT r.* FROM sys_auth_role r " +
            "INNER JOIN sys_user u ON u.roleId = r.roleId " +
            "WHERE u.userId = :userId", nativeQuery = true)
    List<SysAuthRole> selectByUserId(@Param("userId") Integer userId);

    /**
     * 根据用户 ID 查询角色列表
     */
    @Query(value = "SELECT r.* FROM sys_auth_role r " +
            "INNER JOIN sys_user u ON u.roleId = r.roleId " +
            "WHERE u.userId = :userId", nativeQuery = true)
    List<SysAuthRole> findRolesByUserId(@Param("userId") Integer userId);

    /**
     * 删除角色
     */
    @Modifying
    @Query(value = "DELETE FROM sys_auth_role WHERE roleId = :roleId", nativeQuery = true)
    int delete(@Param("roleId") Integer roleId);

    /**
     * 根据角色标识查询角色
     */
    @Query(value = "SELECT * FROM sys_auth_role WHERE roleKey = :roleKey", nativeQuery = true)
    SysAuthRole findByRoleKey(@Param("roleKey") String roleKey);

    default List<SysAuthRole> selectAll() {
        return findAll();
    }

    default int add(SysAuthRole role) {
        save(role);
        return 1;
    }

    default int update(SysAuthRole role) {
        save(role);
        return 1;
    }
}

package com.xiaozhi.role.domain.repository;

import com.xiaozhi.role.domain.Role;

import java.util.Optional;

/**
 * Role 聚合根仓储接口（领域层定义，基础设施层实现）。
 */
public interface RoleRepository {

    /** 按 ID 加载聚合根（不校验所有权） */
    Optional<Role> findById(Integer roleId);

    /**
     * 持久化聚合根（新建或更新）。
     * <p>新建时 {@link Role#assignId(Integer)} 会被调用回填自增 ID。
     * 若 {@code role.isDefault()} 为 true，实现类负责将同用户其他角色的
     * isDefault 重置为 "0"（维护"唯一默认角色"不变式）。
     */
    void save(Role role);

    /** 删除角色（同时清理缓存） */
    void delete(Integer roleId);
}

package com.xiaozhi.role.infrastructure;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.event.RoleUpdatedEvent;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.role.dal.mysql.mapper.RoleMapper;
import com.xiaozhi.role.domain.Role;
import com.xiaozhi.role.domain.repository.RoleRepository;
import com.xiaozhi.role.infrastructure.convert.RoleConverter;
import com.xiaozhi.role.service.RoleService;
import jakarta.annotation.Resource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Role 聚合根仓储实现。
 * <p>
 * 封装 MyBatis-Plus Mapper，负责：
 * <ul>
 *   <li>DO ↔ 聚合根转换（通过 {@link RoleConverter}）</li>
 *   <li>"唯一默认角色"不变式维护（save 时 reset 同用户其他角色）</li>
 *   <li>缓存失效</li>
 * </ul>
 */
@Repository
public class RoleRepositoryImpl implements RoleRepository {

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private RoleConverter roleConverter;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private CacheHelper cacheHelper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Optional<Role> findById(Integer roleId) {
        if (roleId == null) return Optional.empty();
        return Optional.ofNullable(roleMapper.selectById(roleId))
                .map(d -> toRole(d));
    }

    @Override
    @Transactional
    public void save(Role role) {
        RoleDO dataObject = roleConverter.toDataObject(role);

        if (role.getRoleId() == null) {
            if (role.isDefault()) {
                resetDefault(role.getUserId());
            }
            roleMapper.insert(dataObject);
            role.assignId(dataObject.getRoleId());
        } else {
            if (role.isDefault()) {
                resetDefault(role.getUserId(), role.getRoleId());
            }
            roleMapper.updateById(dataObject);
        }

        evictCache(role.getRoleId());

        var signals = role.pullSignals();
        if (signals.contains(Role.DomainSignal.UPDATED)) {
            eventPublisher.publishEvent(new RoleUpdatedEvent(this, role.getRoleId()));
        }
    }

    @Override
    @Transactional
    public void delete(Integer roleId) {
        if (roleId == null) return;
        RoleDO existing = roleMapper.selectById(roleId);
        if (existing != null) {
            roleMapper.delete(new LambdaUpdateWrapper<RoleDO>().eq(RoleDO::getRoleId, roleId));
            evictCache(roleId);
            eventPublisher.publishEvent(new RoleUpdatedEvent(this, roleId));
        }
    }

    /** 重置同用户所有角色的默认标记（insert 前调用） */
    private void resetDefault(Integer userId) {
        roleMapper.update(null, new LambdaUpdateWrapper<RoleDO>()
                .eq(RoleDO::getUserId, userId)
                .set(RoleDO::getIsDefault, "0"));
    }

    /** 重置同用户其他角色的默认标记（update 前调用，排除自身） */
    private void resetDefault(Integer userId, Integer excludeRoleId) {
        roleMapper.update(null, new LambdaUpdateWrapper<RoleDO>()
                .eq(RoleDO::getUserId, userId)
                .ne(RoleDO::getRoleId, excludeRoleId)
                .set(RoleDO::getIsDefault, "0"));
    }

    private void evictCache(Integer roleId) {
        if (roleId == null) return;
        Cache cache = cacheManager.getCache(RoleService.CACHE_NAME);
        if (cache != null) cache.evict(String.valueOf(roleId));
    }

    private Role toRole(RoleDO d) {
        return roleConverter.toDomain(d);
    }
}

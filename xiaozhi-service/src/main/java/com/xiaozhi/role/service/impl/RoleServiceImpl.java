package com.xiaozhi.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.role.domain.Role;
import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.common.config.CacheNames;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.role.convert.RoleConvert;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.role.dal.mysql.mapper.RoleMapper;
import com.xiaozhi.role.model.RoleProjection;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.role.support.RoleCacheKeys;
import jakarta.annotation.Resource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    // 缓存名称统一定义在 RoleService 接口中

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private RoleConvert roleConvert;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private CacheHelper cacheHelper;

    @Override
    public PageResult<RoleProjection> page(int pageNo, int pageSize, Integer roleId, String roleName,
                                           String isDefault, String state, Integer userId) {
        Page<RoleProjection> page = new Page<>(pageNo, pageSize);
        IPage<RoleProjection> result = roleMapper.selectPage(page, roleId, roleName, isDefault, state, userId);
        List<RoleProjection> records = result.getRecords();
        return new PageResult<>(
            records,
            result.getTotal(),
            Math.toIntExact(result.getCurrent()),
            Math.toIntExact(result.getSize())
        );
    }

    @Override
    public RoleBO getBO(Integer roleId) {
        if (roleId == null) {
            return null;
        }
        String cacheKey = RoleCacheKeys.of(roleId);
        Cache cache = cacheManager.getCache(CacheNames.ROLE);
        return cacheHelper.getWithLock(
            "role:" + cacheKey,
            () -> cache == null ? null : cache.get(cacheKey, RoleBO.class),
            () -> {
                RoleDO roleDO = getRole(roleId);
                if (roleDO == null) return null;
                RoleBO roleBO = roleConvert.toBO(roleDO);
                if (cache != null) cache.put(cacheKey, roleBO);
                return roleBO;
            }
        );
    }

    @Override
    public List<RoleBO> listBO(Integer userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        List<RoleBO> roles = roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getUserId, userId)
                .eq(RoleDO::getState, Role.STATE_ENABLED)
                .orderByDesc(RoleDO::getIsDefault, RoleDO::getCreateTime)
                .last("LIMIT " + limit))
            .stream()
            .map(roleConvert::toBO)
            .toList();
        return roles;
    }

    @Override
    public RoleBO getDefaultOrFirstBO(Integer userId) {
        RoleDO roleDO = findDefaultOrFirstDO(userId);
        if (roleDO == null) {
            return null;
        }
        RoleBO roleBO = roleConvert.toBO(roleDO);
        return roleBO;
    }

    @Override
    @Transactional
    public Integer copyDefaultRole(Integer sourceUserId, Integer targetUserId) {

        RoleDO sourceRole = findDefaultOrFirstDO(sourceUserId);
        if (sourceRole == null) {
            throw new IllegalStateException("默认角色模板不存在");
        }

        resetDefault(targetUserId);

        RoleDO copiedRole = roleConvert.copy(sourceRole);
        copiedRole.setUserId(targetUserId);
        copiedRole.setIsDefault("1");
        if (roleMapper.insert(copiedRole) <= 0) {
            throw new IllegalStateException("复制默认角色失败");
        }
        if (copiedRole.getRoleId() == null) {
            throw new IllegalStateException("复制默认角色失败");
        }
        return copiedRole.getRoleId();
    }

    private RoleDO getRole(Integer roleId) {
        if (roleId == null) {
            return null;
        }
        return roleMapper.selectById(roleId);
    }

    private RoleDO findDefaultOrFirstDO(Integer userId) {
        if (userId == null) {
            return null;
        }

        return roleMapper.selectOne(new LambdaQueryWrapper<RoleDO>()
            .eq(RoleDO::getUserId, userId)
            .eq(RoleDO::getState, Role.STATE_ENABLED)
            .orderByDesc(RoleDO::getIsDefault)
            .orderByAsc(RoleDO::getRoleId)
            .last("LIMIT 1"));
    }

    /** 重置同用户其他默认角色的标记，并让被降级角色的缓存失效（与 RoleRepositoryImpl#resetDefault 保持一致） */
    private void resetDefault(Integer userId) {
        List<Integer> demotedRoleIds = roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                .select(RoleDO::getRoleId)
                .eq(RoleDO::getUserId, userId)
                .eq(RoleDO::getIsDefault, "1"))
            .stream()
            .map(RoleDO::getRoleId)
            .toList();
        if (demotedRoleIds.isEmpty()) {
            return;
        }
        roleMapper.update(null, new LambdaUpdateWrapper<RoleDO>()
            .in(RoleDO::getRoleId, demotedRoleIds)
            .set(RoleDO::getIsDefault, "0"));
        Cache cache = cacheManager.getCache(CacheNames.ROLE);
        demotedRoleIds.forEach(id -> CacheHelper.evictNow(cache, RoleCacheKeys.of(id)));
    }

}

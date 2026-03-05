package com.xiaozhi.service.impl;

import com.xiaozhi.common.cache.CacheHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.repository.SysRoleRepository;
import com.xiaozhi.service.SysRoleService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色操作
 *
 * @author Joey
 *
 */

@Service
public class SysRoleServiceImpl extends BaseServiceImpl implements SysRoleService {
    private final static String CACHE_NAME = "XiaoZhi:SysRole";

    @Resource
    private SysRoleRepository sysRoleRepository;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Resource
    private CacheHelper cacheHelper;

    /**
     * 添加角色
     *
     * @param role
     * @return
     */
    @Override
    @Transactional
    public int add(SysRole role) {
        // 如果当前配置被设置为默认，则将同类型同用户的其他配置设置为非默认
        if (role.getIsDefault() != null && role.getIsDefault().equals("1")) {
            sysRoleRepository.resetDefault(role.getUserId());
        }
        // 添加角色
        sysRoleRepository.save(role);
        return 1;
    }

    /**
     * 查询角色信息
     * 指定分页信息
     * @param role
     * @param pageFilter
     * @return
     */
    @Override
    public List<SysRole> query(SysRole role, PageFilter pageFilter) {
        if (pageFilter != null) {
            Page<SysRole> page = sysRoleRepository.findRoles(
                    role.getUserId(),
                    role.getState(),
                    role.getIsDefault(),
                    PageRequest.of(pageFilter.getStart() - 1, pageFilter.getLimit(), Sort.by(Sort.Direction.DESC, "createTime"))
            );
            return page.getContent();
        }
        return sysRoleRepository.findRoles(
                role.getUserId(),
                role.getState(),
                role.getIsDefault(),
                PageRequest.of(0, 10)
        ).getContent();
    }

    /**
     * 更新角色信息
     *
     * @param role
     * @return
     */
    @Override
    @Transactional
    public int update(SysRole role) {
        // 如果当前配置被设置为默认，则将同类型同用户的其他配置设置为非默认
        if (role.getIsDefault() != null && role.getIsDefault().equals("1")) {
            sysRoleRepository.resetDefault(role.getUserId());
        }

        sysRoleRepository.save(role);
        int result = 1;

        // 如果更新成功且 roleId 不为空，直接将更新后的完整对象加载到缓存中
        if (role.getRoleId() != null && cacheManager != null) {
            // 直接从数据库查询最新数据
            SysRole updatedRole = sysRoleRepository.findRoleById(role.getRoleId()).orElse(null);
            // 手动更新缓存
            if (updatedRole != null) {
                Cache cache = cacheManager.getCache(CACHE_NAME);
                if (cache != null) {
                    cache.put(updatedRole.getRoleId(), updatedRole);
                }
            }
        }

        return result;
    }

    /**
     * 删除角色
     *
     * @param roleId
     * @return
     */
    @Override
    @Transactional
    public int deleteById(Integer roleId) {
        int result = sysRoleRepository.deleteRoleById(roleId);

        // 如果删除成功，清除缓存
        if (result > 0 && cacheManager != null) {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.evict(roleId);
            }
        }

        return result;
    }

    @Override
    public SysRole selectRoleById(Integer roleId) {
        // 使用分布式锁防止缓存击穿 (特别是默认角色的高并发访问)
        return cacheHelper.getWithLock(
            "role:" + roleId,
            // 从缓存获取
            () -> {
                if (cacheManager != null) {
                    Cache cache = cacheManager.getCache(CACHE_NAME);
                    if (cache != null) {
                        Cache.ValueWrapper wrapper = cache.get(roleId);
                        if (wrapper != null) {
                            return (SysRole) wrapper.get();
                        }
                    }
                }
                return null;
            },
            // 从数据库获取
            () -> {
                SysRole role = sysRoleRepository.findRoleById(roleId).orElse(null);

                // 手动写入缓存
                if (role != null && cacheManager != null) {
                    Cache cache = cacheManager.getCache(CACHE_NAME);
                    if (cache != null) {
                        cache.put(roleId, role);
                    }
                }

                return role;
            }
        );
    }
}

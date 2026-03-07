package com.xiaozhi.service.impl;

import com.xiaozhi.dao.PermissionMapper;
import com.xiaozhi.entity.SysPermission;
import com.xiaozhi.service.SysPermissionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SysPermissionServiceImpl implements SysPermissionService {

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PERMISSION_CACHE_PREFIX = "USER_PERMISSION:";
    private static final long CACHE_EXPIRE_TIME = 30; // 30分钟

    @Override
    public List<SysPermission> selectAll() {
        return permissionMapper.selectAll();
    }

    @Override
    public SysPermission selectById(Integer permissionId) {
        return permissionMapper.selectById(permissionId);
    }

    @Override
    public List<SysPermission> selectByType(String permissionType) {
        return permissionMapper.selectByType(permissionType);
    }

    @Override
    public List<SysPermission> selectByParentId(Integer parentId) {
        return permissionMapper.selectByParentId(parentId);
    }

    @Override
    public List<SysPermission> selectByRoleId(Integer roleId) {
        return permissionMapper.selectByRoleId(roleId);
    }

    @Override
    public List<SysPermission> selectByUserId(Integer userId) {
        // 1. 尝试从缓存获取
        String cacheKey = PERMISSION_CACHE_PREFIX + userId;
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                return objectMapper.readValue(cachedJson, new TypeReference<List<SysPermission>>() {});
            }
        } catch (Exception e) {
            // 缓存读取失败，继续从数据库查询
        }

        // 2. 从数据库查询
        List<SysPermission> permissions = permissionMapper.selectByUserId(userId);

        // 3. 存入缓存
        try {
            String json = objectMapper.writeValueAsString(permissions);
            stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_EXPIRE_TIME, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 缓存写入失败，不影响业务逻辑
        }

        return permissions;
    }

    /**
     * 清除用户权限缓存
     *
     * @param userId 用户ID
     */
    public void clearUserPermissionCache(Integer userId) {
        String cacheKey = PERMISSION_CACHE_PREFIX + userId;
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (Exception e) {
            // 缓存清除失败，记录日志
        }
    }

    /**
     * 清除所有权限缓存
     */
    public void clearAllPermissionCache() {
        try {
            var keys = stringRedisTemplate.keys(PERMISSION_CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // 缓存清除失败，记录日志
        }
    }

    @Override
    public List<SysPermission> buildPermissionTree(List<SysPermission> permissions) {
        List<SysPermission> returnList = new ArrayList<>();
        
        // 先找出所有的一级菜单
        for (SysPermission permission : permissions) {
            // 一级菜单没有parentId或parentId为0
            if (permission.getParentId() == null || permission.getParentId() == 0) {
                permission.setChildren(new ArrayList<>());
                returnList.add(permission);
            }
        }
        
        // 为一级菜单设置子菜单
        for (SysPermission permission : permissions) {
            if (permission.getParentId() != null && permission.getParentId() != 0) {
                // 获取父菜单
                for (SysPermission parent : returnList) {
                    if (parent.getPermissionId().equals(permission.getParentId())) {
                        if (parent.getChildren() == null) {
                            parent.setChildren(new ArrayList<>());
                        }
                        parent.getChildren().add(permission);
                        break;
                    }
                }
            }
        }
        
        return returnList;
    }

    @Override
    public int add(SysPermission permission) {
        return permissionMapper.add(permission);
    }

    @Override
    public int update(SysPermission permission) {
        return permissionMapper.update(permission);
    }

    @Override
    public int delete(Integer permissionId) {
        return permissionMapper.delete(permissionId);
    }
}
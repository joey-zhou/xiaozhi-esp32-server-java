package com.xiaozhi.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaozhi.authrolepermission.dal.mysql.dataobject.AuthRolePermissionDO;
import com.xiaozhi.authrolepermission.dal.mysql.mapper.AuthRolePermissionMapper;
import com.xiaozhi.common.model.bo.PermissionBO;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.permission.convert.PermissionConvert;
import com.xiaozhi.permission.dal.mysql.dataobject.PermissionDO;
import com.xiaozhi.permission.dal.mysql.mapper.PermissionMapper;
import com.xiaozhi.permission.service.PermissionService;
import com.xiaozhi.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PermissionServiceImpl implements PermissionService {

    private static final String ENABLED = "1";

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private AuthRolePermissionMapper authRolePermissionMapper;

    @Resource
    private UserService userService;

    @Resource
    private PermissionConvert permissionConvert;

    // 自注入以解决Spring AOP自调用缓存失效问题
    @Lazy
    @Autowired
    private PermissionService self;

    @Override
    public List<PermissionBO> listTree() {
        List<PermissionBO> permissions = permissionMapper.selectList(new LambdaQueryWrapper<PermissionDO>()
                .eq(PermissionDO::getStatus, ENABLED)
                .orderByAsc(PermissionDO::getSort, PermissionDO::getPermissionId))
            .stream()
            .map(permissionConvert::toBO)
            .toList();
        return buildTree(permissions);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'authRole:list:' + #authRoleId", condition = "#authRoleId != null")
    public List<PermissionBO> listByAuthRoleId(Integer authRoleId) {
        if (authRoleId == null) {
            return new ArrayList<>();
        }

        List<Integer> permissionIds = self.listIdsByAuthRoleId(authRoleId);
        if (CollectionUtils.isEmpty(permissionIds)) {
            return new ArrayList<>();
        }

        return permissionMapper.selectList(new LambdaQueryWrapper<PermissionDO>()
                .in(PermissionDO::getPermissionId, permissionIds)
                .eq(PermissionDO::getStatus, ENABLED)
                .orderByAsc(PermissionDO::getSort, PermissionDO::getPermissionId))
            .stream()
            .map(permissionConvert::toBO)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'authRole:ids:' + #authRoleId", condition = "#authRoleId != null")
    public List<Integer> listIdsByAuthRoleId(Integer authRoleId) {
        if (authRoleId == null) {
            return new ArrayList<>();
        }
        return authRolePermissionMapper.selectList(new LambdaQueryWrapper<AuthRolePermissionDO>()
                .eq(AuthRolePermissionDO::getAuthRoleId, authRoleId)
                .orderByAsc(AuthRolePermissionDO::getPermissionId))
            .stream()
            .map(AuthRolePermissionDO::getPermissionId)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = CACHE_NAME, key = "'authRole:list:' + #authRoleId"),
        @CacheEvict(value = CACHE_NAME, key = "'authRole:ids:' + #authRoleId")
    })
    public void clearAuthRoleCache(Integer authRoleId) {
    }

    @Override
    public List<PermissionBO> listByUserId(Integer userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        UserBO user = userService.getBO(userId);
        Integer authRoleId = user == null ? null : user.getAuthRoleId();
        if (authRoleId == null) {
            return new ArrayList<>();
        }
        return self.listByAuthRoleId(authRoleId);
    }

    @Override
    public List<PermissionBO> listTreeByUserId(Integer userId) {
        return buildTree(listByUserId(userId));
    }

    @Override
    public List<String> listKeysByUserId(Integer userId) {
        return listByUserId(userId).stream()
            .map(PermissionBO::getPermissionKey)
            .filter(StringUtils::hasText)
            .toList();
    }

    /** 直接在入参节点上挂 children，每个节点的 children 都会先重置为空。 */
    private List<PermissionBO> buildTree(List<PermissionBO> permissions) {
        if (permissions.isEmpty()) {
            return List.of();
        }

        Map<Integer, PermissionBO> nodeMap = new LinkedHashMap<>();
        for (PermissionBO permission : permissions) {
            permission.setChildren(new ArrayList<>());
            nodeMap.put(permission.getPermissionId(), permission);
        }

        List<PermissionBO> roots = new ArrayList<>();
        for (PermissionBO node : nodeMap.values()) {
            Integer parentId = node.getParentId();
            if (parentId == null || parentId == 0 || !nodeMap.containsKey(parentId)) {
                roots.add(node);
                continue;
            }
            nodeMap.get(parentId).getChildren().add(node);
        }
        return roots;
    }
}

package com.xiaozhi.security;

import cn.dev33.satoken.stp.StpInterface;
import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.permission.service.PermissionService;
import com.xiaozhi.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 权限接口实现，给框架提供用户的权限 key 与角色 key。
 */
@Slf4j
@Component
public class StpInterfaceImpl implements StpInterface {

    private static final String ADMIN_ROLE_KEY = "admin";

    @Resource
    private PermissionService permissionService;

    @Resource
    private UserService userService;

    @Resource
    private AuthRoleService authRoleService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        try {
            return permissionService.listKeysByUserId(parseUserId(loginId));
        } catch (Exception e) {
            log.error("获取用户权限失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 超级管理员的 admin 先于 roleKey 追加，authRoleId 指向已删角色时也不会丢。 */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> roleKeys = new ArrayList<>();
        try {
            UserBO user = userService.getBO(parseUserId(loginId));
            if (user == null) {
                return roleKeys;
            }
            if (UserBO.ADMIN_YES.equals(user.getIsAdmin())) {
                roleKeys.add(ADMIN_ROLE_KEY);
            }
            String roleKey = authRoleService.getRoleKey(user.getAuthRoleId());
            if (StringUtils.hasText(roleKey)) {
                roleKeys.add(roleKey);
            }
        } catch (Exception e) {
            log.error("获取用户角色失败: {}", e.getMessage());
        }
        return roleKeys;
    }

    private static Integer parseUserId(Object loginId) {
        return Integer.parseInt(loginId.toString());
    }
}

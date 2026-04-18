package com.xiaozhi.security;

import cn.dev33.satoken.stp.StpInterface;
import com.xiaozhi.authrole.service.AuthRoleService;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.resp.AuthRoleResp;
import com.xiaozhi.common.model.resp.PermissionResp;
import com.xiaozhi.permission.service.PermissionService;
import com.xiaozhi.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
/**
 * Sa-Token权限接口实现
 * 用于Sa-Token框架获取用户的权限和角色信息
 *
 * @author Joey
 */
@Slf4j
@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private PermissionService permissionService;

    @Resource
    private UserService userService;

    @Resource
    private AuthRoleService authRoleService;

    /**
     * 返回用户的权限列表
     *
     * @param loginId 用户ID
     * @param loginType 登录类型
     * @return 权限列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> permissionList = new ArrayList<>();

        try {
            Integer userId = Integer.parseInt(loginId.toString());

            // 查询用户权限
            List<PermissionResp> permissions = permissionService.listByUserId(userId);

            // 提取权限key
            for (PermissionResp permission : permissions) {
                if (permission.getPermissionKey() != null && !permission.getPermissionKey().isEmpty()) {
                    permissionList.add(permission.getPermissionKey());
                }
            }
        } catch (Exception e) {
            // 记录日志但不抛出异常,返回空列表
            log.error("获取用户权限失败: {}", e.getMessage());
        }

        return permissionList;
    }

    /**
     * 返回用户的角色列表
     *
     * @param loginId 用户ID
     * @param loginType 登录类型
     * @return 角色列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> authRoleKeys = new ArrayList<>();

        try {
            Integer userId = Integer.parseInt(loginId.toString());

            // 查询用户信息
            UserBO user = userService.getBO(userId);

            if (user != null && user.getAuthRoleId() != null) {
                // 查询后台权限角色信息
                AuthRoleResp authRole = authRoleService.get(user.getAuthRoleId());

                if (authRole != null && authRole.getRoleKey() != null && !authRole.getRoleKey().isEmpty()) {
                    authRoleKeys.add(authRole.getRoleKey());
                }

                // 如果是超级管理员,添加admin角色
                if ("1".equals(user.getIsAdmin())) {
                    authRoleKeys.add("admin");
                }
            }
        } catch (Exception e) {
            // 记录日志但不抛出异常,返回空列表
            log.error("获取用户角色失败: {}", e.getMessage());
        }

        return authRoleKeys;
    }
}

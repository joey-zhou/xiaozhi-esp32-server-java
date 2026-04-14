package com.xiaozhi.utils;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.user.service.UserService;

/**
 * 认证工具类
 * 提供统一的用户信息获取方法
 *
 * @author Joey
 */
public class AuthUtils {

    private static UserService userService;

    /**
     * 注入UserService(通过Spring容器注入)
     */
    public static void setUserService(UserService userService) {
        AuthUtils.userService = userService;
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 用户ID
     */
    public static Integer getCurrentUserId() {
        try {
            return StpUtil.getLoginIdAsInt();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息
     */
    public static UserBO getCurrentUser() {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }

        try {
            return userService.getBO(userId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查是否已登录
     *
     * @return 是否已登录
     */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

    /**
     * 检查当前用户是否有指定权限
     *
     * @param permission 权限标识
     * @return 是否有权限
     */
    public static boolean hasPermission(String permission) {
        try {
            StpUtil.checkPermission(permission);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查当前用户是否有指定角色
     *
     * @param role 角色标识
     * @return 是否有角色
     */
    public static boolean hasRole(String role) {
        try {
            StpUtil.checkRole(role);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 退出登录
     */
    public static void logout() {
        StpUtil.logout();
    }

    /**
     * 获取当前Token
     *
     * @return Token
     */
    public static String getToken() {
        try {
            return StpUtil.getTokenValue();
        } catch (Exception e) {
            return null;
        }
    }
}

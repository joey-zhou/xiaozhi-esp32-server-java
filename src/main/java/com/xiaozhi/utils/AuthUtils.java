package com.xiaozhi.utils;

import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 认证工具类
 * 提供统一的用户信息获取方法
 *
 * @author Joey
 */
public class AuthUtils {

    private static SysUserService userService;

    /**
     * 注入 UserService(通过 Spring 容器注入)
     */
    public static void setUserService(SysUserService userService) {
        AuthUtils.userService = userService;
    }

    /**
     * 获取当前登录用户 ID
     *
     * @return 用户 ID
     */
    public static Integer getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                String username = userDetails.getUsername();
                SysUser user = userService.selectUserByUsername(username);
                return user != null ? user.getUserId() : null;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息
     */
    public static SysUser getCurrentUser() {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }

        try {
            return userService.selectUserByUserId(userId);
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
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.isAuthenticated()
                    && !(authentication.getPrincipal() instanceof String)
                    && "anonymousUser".equals(authentication.getPrincipal());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查当前用户是否有指定权限
     *
     * @param permission 权限标识
     * @return 是否有权限
     */
    public static boolean hasPermission(String permission) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(permission));
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
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_" + role));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 退出登录
     */
    public static void logout() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 获取当前 Token
     *
     * @return Token
     */
    public static String getToken() {
        // Spring Security 默认不存储 Token，返回 null
        return null;
    }
}

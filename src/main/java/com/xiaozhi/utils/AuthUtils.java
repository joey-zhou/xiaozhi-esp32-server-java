package com.xiaozhi.utils;

import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * 认证工具类
 * 基于 Spring Security 提供统一的用户信息获取方法
 *
 * @author Joey
 */
@Slf4j
@Component
public class AuthUtils {

    private static SysUserService userService;

    /**
     * 注入 UserService(通过 Spring 容器注入)
     */
    public AuthUtils(SysUserService userService) {
        AuthUtils.userService = userService;
    }

    /**
     * 获取当前登录用户 ID
     *
     * @return 用户 ID，未登录返回 null
     */
    public static Integer getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            Object principal = authentication.getPrincipal();
            
            // 如果是 UserDetails 类型，从中获取用户名
            if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                SysUser user = userService.selectUserByUsername(username);
                return user != null ? user.getUserId() : null;
            }
            
            // 如果是 SysUser 类型，直接返回
            if (principal instanceof SysUser) {
                return ((SysUser) principal).getUserId();
            }
            
            // 尝试解析为 Integer
            if (principal instanceof Integer) {
                return (Integer) principal;
            }
            
            return null;
        } catch (Exception e) {
            log.debug("获取当前用户 ID 失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息，未登录返回 null
     */
    public static SysUser getCurrentUser() {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }

        try {
            return userService.selectUserByUserId(userId);
        } catch (Exception e) {
            log.debug("获取当前用户信息失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前登录用户名
     *
     * @return 用户名，未登录返回 null
     */
    public static String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            }
            if (principal instanceof SysUser) {
                return ((SysUser) principal).getUsername();
            }
            return principal.toString();
        } catch (Exception e) {
            log.debug("获取当前用户名失败：{}", e.getMessage());
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
                    && !(authentication.getPrincipal() instanceof String);
        } catch (Exception e) {
            log.debug("检查登录状态失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查当前用户是否有指定角色
     *
     * @param role 角色标识（如：ROLE_ADMIN 或 ADMIN）
     * @return 是否有角色
     */
    public static boolean hasRole(String role) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return false;
            }

            // 自动添加 ROLE_ 前缀
            String targetRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            
            return authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(targetRole));
        } catch (Exception e) {
            log.debug("检查角色失败：{}", e.getMessage());
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
            if (authentication == null || !authentication.isAuthenticated()) {
                return false;
            }

            return authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(permission));
        } catch (Exception e) {
            log.debug("检查权限失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查当前用户是否为管理员
     *
     * @return 是否为管理员
     */
    public static boolean isAdmin() {
        return hasRole("ADMIN") || hasRole("ROLE_1");
    }
}

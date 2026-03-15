package com.xiaozhi.utils;

import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 认证工具类
 * 提供统一的用户信息获取方法
 *
 * @author Joey
 */
public class AuthUtils {

    private static SysUserService userService;
    public static final String USER_ATTRIBUTE_KEY = "user";
    private static final ThreadLocal<String> REQUEST_URI = new ThreadLocal<>();
    private static final ThreadLocal<String> METHOD = new ThreadLocal<>();
    private static final ThreadLocal<String> API_PATH = new ThreadLocal<>();

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
     * 获取当前请求 URI
     */
    public static String getRequestUri() {
        return REQUEST_URI.get();
    }

    public static void setUser(HttpServletRequest request, SysUser user) {
        request.setAttribute(USER_ATTRIBUTE_KEY, user);
    }

    public static SysUser getUser() {
        Object userObj = RequestContextHolder.currentRequestAttributes().getAttribute(USER_ATTRIBUTE_KEY, RequestAttributes.SCOPE_REQUEST);
        if (userObj instanceof SysUser) {
            return (SysUser) userObj;
        } else {
            if (null == userObj) {
                return getCurrentUser();
            }
            return null;
        }
    }

    public static String getUsername() {
        SysUser user = getUser();
        if (user != null) {
            return user.getUsername();
        } else {
            return null;
        }
    }

    public static String getName() {
        SysUser user = getUser();
        if (user != null) {
            return user.getName();
        } else {
            return null;
        }
    }

    /**
     * 设置请求信息
     */
    public static void setRequestInfo(String requestUri, String method, String apiPath) {
        REQUEST_URI.set(requestUri);
        METHOD.set(method);
        API_PATH.set(apiPath);
    }


    /**
     * 获取当前请求方法
     */
    public static String getMethod() {
        return METHOD.get();
    }

    /**
     * 获取当前 API 路径
     */
    public static String getApiPath() {
        return API_PATH.get();
    }

    /**
     * 获取完整的请求信息
     */
    public static String getFullRequestInfo() {
        String method = METHOD.get();
        String uri = REQUEST_URI.get();
        if (method != null && uri != null) {
            return method + " " + uri;
        }
        return "未知请求";
    }

    /**
     * 清除请求信息
     */
    public static void clear() {
        REQUEST_URI.remove();
        METHOD.remove();
        API_PATH.remove();
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

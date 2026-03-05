package com.xiaozhi.security.service;

import com.xiaozhi.entity.SysUser;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.security.CustomUserDetailsService;
import com.xiaozhi.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * JWT 认证服务
 * 处理用户登录、登出等认证相关操作
 *
 * @author Joey
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtAuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final AuthenticationService passwordService;

    /**
     * 用户登录
     *
     * @param username 用户名/邮箱/手机号
     * @param password 明文密码
     * @return JWT 令牌
     */
    public String login(String username, String password) {
        log.debug("用户登录：{}", username);

        try {
            // 通过用户名/邮箱/手机号查询用户
            SysUser user = userDetailsService.findUserByUsername(username);
            if (user == null) {
                throw new AuthenticationException("用户不存在") {};
            }

            // 验证密码（支持 MD5 和 BCrypt 两种格式）
            if (!passwordService.isPasswordValid(password, user.getPassword())) {
                throw new AuthenticationException("密码错误") {};
            }

            // 创建认证令牌
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            // 认证成功，设置到 SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authToken);

            // 生成 JWT 令牌
            return jwtTokenProvider.generateToken(userDetails, user.getUserId());

        } catch (AuthenticationException e) {
            log.error("认证失败：{}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * 刷新令牌
     */
    public String refreshToken(String token) {
        return jwtTokenProvider.refreshToken(token);
    }

    /**
     * 登出
     */
    public void logout() {
        SecurityContextHolder.clearContext();
        log.debug("用户登出");
    }

    /**
     * 获取当前登录用户 ID
     */
    public Integer getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            SysUser user = userDetailsService.findUserByUsername(username);
            if (user != null) {
                return user.getUserId();
            }
        }
        return null;
    }

    /**
     * 获取当前登录用户名
     */
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return null;
    }

    /**
     * 检查是否已登录
     */
    public boolean isLogin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}

package com.xiaozhi.security;

import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 用户详情服务
 * 用于 Spring Security 加载用户信息
 *
 * @author Joey
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final SysUserService userService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("加载用户详情：{}", username);
        
        SysUser user = findUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        
        // 检查用户状态
        if ("0".equals(user.getState())) {
            throw new UsernameNotFoundException("用户已被禁用：" + username);
        }
        
        // 构建用户权限列表
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_" + (user.getRoleId() != null ? user.getRoleId() : 2))
        );
        
        return new User(
            user.getUsername(),
            user.getPassword(),
            authorities
        );
    }

    /**
     * 通过用户名/邮箱/手机号查找用户
     */
    @Transactional(readOnly = true)
    public SysUser findUserByUsername(String username) {
        // 尝试通过用户名查询
        SysUser user = userService.selectUserByUsername(username);
        if (user == null) {
            // 尝试通过邮箱查询
            user = userService.selectUserByEmail(username);
        }
        if (user == null) {
            // 尝试通过手机号查询
            user = userService.selectUserByTel(username);
        }
        return user;
    }

    /**
     * 根据用户 ID 加载用户详情
     */
    public UserDetails loadUserByUserId(Integer userId) {
        log.debug("根据用户 ID 加载用户详情：{}", userId);
        
        SysUser user = userService.selectUserByUserId(userId);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在，ID: " + userId);
        }
        
        // 检查用户状态
        if ("0".equals(user.getState())) {
            throw new UsernameNotFoundException("用户已被禁用：" + user.getUsername());
        }
        
        // 构建用户权限列表
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_" + (user.getRoleId() != null ? user.getRoleId() : 2))
        );
        
        return new User(
            user.getUsername(),
            user.getPassword(),
            authorities
        );
    }
}

package com.xiaozhi.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 DTO
 *
 * @author Joey
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * 访问令牌
     */
    private String token;

    /**
     * 刷新令牌（暂未使用）
     */
    private String refreshToken;

    /**
     * 令牌过期时间（秒）
     */
    private Long expiresIn;

    /**
     * 用户 ID
     */
    private Integer userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户角色 ID
     */
    private Integer roleId;

    /**
     * 是否管理员
     */
    private String isAdmin;
}

package com.xiaozhi.security.service.impl;

import com.xiaozhi.security.service.AuthenticationService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 密码加密与验证，算法固定为 BCrypt。
 * 每次加密自带随机盐，同一明文两次加密的结果不同，只能用 isPasswordValid 比对，不能直接比字符串。
 *
 * @author Joey
 *
 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final int BCRYPT_STRENGTH = 10;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    /**
     * @param rawPassword
     * @return 加密后的密码
     */
    @Override
    public String encryptPassword(String rawPassword) {
        if (!StringUtils.hasLength(rawPassword)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 密码验证
     *
     * @param rawPassword
     * @param encryptPassword
     * @return 是否相同
     */
    @Override
    public Boolean isPasswordValid(String rawPassword, String encryptPassword) {
        if (!StringUtils.hasLength(rawPassword) || !StringUtils.hasText(encryptPassword)) {
            return false;
        }
        // 库里残留的非 BCrypt 哈希（如历史 MD5）一律判为不匹配
        return passwordEncoder.matches(rawPassword, encryptPassword);
    }

}

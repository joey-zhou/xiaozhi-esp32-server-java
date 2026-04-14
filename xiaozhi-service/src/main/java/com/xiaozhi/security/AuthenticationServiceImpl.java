package com.xiaozhi.security;

import com.xiaozhi.utils.CommonUtils;
import org.springframework.stereotype.Service;

/**
 * 密码加密与验证
 *
 * @author Joey
 *
 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final String salt = "joey@zhou";

    /**
     * @param rawPassword
     * @return 加密后的密码
     */
    public String encryptPassword(String rawPassword) {
        String saltPassword = rawPassword + salt;
        return CommonUtils.md5(saltPassword);
    }

    /**
     * 密码验证
     * 
     * @param rawPassword
     * @param encryptPassword
     * @return 是否相同
     */
    public Boolean isPasswordValid(String rawPassword, String encryptPassword) {
        String encodePassword = encryptPassword(rawPassword);
        return encodePassword.equals(encryptPassword);
    }

}
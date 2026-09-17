package com.xiaozhi.security.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 钉住 BCrypt 的两个每次加密带随机盐所以结果不可比字符串，
 * 库里残留的非 BCrypt 散列（历史 MD5）一律验不过。
 */
class AuthenticationServiceImplTest {

    private final AuthenticationServiceImpl authenticationService = new AuthenticationServiceImpl();

    @Test
    void encryptPasswordProducesDifferentHashEachTime() {
        String encrypted = authenticationService.encryptPassword("secret");

        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).isNotEqualTo("secret");
        assertThat(encrypted).startsWith("$2a$10$");
        assertThat(authenticationService.encryptPassword("secret")).isNotEqualTo(encrypted);
    }

    @Test
    void encryptPasswordRejectsEmptyRawPassword() {
        assertThatThrownBy(() -> authenticationService.encryptPassword(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> authenticationService.encryptPassword(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isPasswordValidMatchesEncryptedPassword() {
        String encrypted = authenticationService.encryptPassword("secret");

        assertThat(authenticationService.isPasswordValid("secret", encrypted)).isTrue();
        assertThat(authenticationService.isPasswordValid("wrong", encrypted)).isFalse();
    }

    @Test
    void isPasswordValidRejectsLegacyMd5AndBlankStoredHash() {
        assertThat(authenticationService.isPasswordValid("123456", "11cd9c061d614dcf37ec60c44c11d2ad")).isFalse();
        assertThat(authenticationService.isPasswordValid("123456", "!RESET_REQUIRED")).isFalse();
        assertThat(authenticationService.isPasswordValid("123456", null)).isFalse();
        assertThat(authenticationService.isPasswordValid(null, "!RESET_REQUIRED")).isFalse();
    }
}

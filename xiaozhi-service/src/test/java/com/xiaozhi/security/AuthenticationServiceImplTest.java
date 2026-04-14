package com.xiaozhi.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationServiceImplTest {

    private final AuthenticationServiceImpl authenticationService = new AuthenticationServiceImpl();

    @Test
    void encryptPasswordReturnsStableHash() {
        String encrypted = authenticationService.encryptPassword("secret");

        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).isNotEqualTo("secret");
        assertThat(authenticationService.encryptPassword("secret")).isEqualTo(encrypted);
    }

    @Test
    void isPasswordValidMatchesEncryptedPassword() {
        String encrypted = authenticationService.encryptPassword("secret");

        assertThat(authenticationService.isPasswordValid("secret", encrypted)).isTrue();
        assertThat(authenticationService.isPasswordValid("wrong", encrypted)).isFalse();
    }
}

package com.xiaozhi.verifycode.sender;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只测脱敏这个纯函数：真正发短信要打阿里云的网络请求，不在单元测试范围内。
 */
class AliyunSmsSenderTest {

    @Test
    void maskPhoneKeepsPrefixAndSuffixOnly() {
        assertThat(AliyunSmsSender.maskPhone("13800138000")).isEqualTo("138****8000");
    }

    @Test
    void maskPhoneReturnsPlaceholderForTooShortInput() {
        assertThat(AliyunSmsSender.maskPhone("123")).isEqualTo("***");
    }

    @Test
    void maskPhoneReturnsPlaceholderForNull() {
        assertThat(AliyunSmsSender.maskPhone(null)).isEqualTo("***");
    }
}

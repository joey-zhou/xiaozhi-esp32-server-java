package com.xiaozhi.verifycode.sender;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只测不发网络请求的纯函数：脱敏、格式校验、未配置 SMTP 时的短路返回，
 * 真正发邮件要连 QQ 邮箱服务器，不在单元测试范围内。
 */
class SmtpEmailSenderTest {

    private final SmtpEmailSender smtpEmailSender = new SmtpEmailSender();

    @Test
    void sendReturnsFalseForInvalidEmailFormat() {
        assertThat(smtpEmailSender.send("not-an-email", "subject", "content", "小智")).isFalse();
    }

    @Test
    void sendReturnsFalseWhenSmtpCredentialsAreNotConfigured() {
        // 未注入 emailUsername/emailPassword，字段保持默认的 null，等同未配置第三方邮箱认证信息
        assertThat(smtpEmailSender.send("alice@example.com", "subject", "content", "小智")).isFalse();
    }

    @Test
    void sendReturnsFalseWhenPasswordIsBlank() {
        ReflectionTestUtils.setField(smtpEmailSender, "emailUsername", "bot@example.com");
        ReflectionTestUtils.setField(smtpEmailSender, "emailPassword", "  ");

        assertThat(smtpEmailSender.send("alice@example.com", "subject", "content", "小智")).isFalse();
    }

    @Test
    void maskEmailKeepsAtMostTwoLocalCharacters() {
        assertThat(SmtpEmailSender.maskEmail("alice@example.com")).isEqualTo("al***@example.com");
    }

    @Test
    void maskEmailReturnsPlaceholderForMissingAtSign() {
        assertThat(SmtpEmailSender.maskEmail("not-an-email")).isEqualTo("***");
    }

    @Test
    void maskEmailReturnsPlaceholderForNull() {
        assertThat(SmtpEmailSender.maskEmail(null)).isEqualTo("***");
    }
}

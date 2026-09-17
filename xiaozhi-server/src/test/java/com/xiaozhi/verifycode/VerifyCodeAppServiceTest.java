package com.xiaozhi.verifycode;

import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.req.UserSendCaptchaReq;
import com.xiaozhi.user.service.UserService;
import com.xiaozhi.verifycode.sender.AliyunSmsSender;
import com.xiaozhi.verifycode.sender.SmtpEmailSender;
import com.xiaozhi.verifycode.service.VerifyCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证码发送编排：注册状态与格式校验要先于生成验证码，避免为非法地址落库；
 * 发送失败/异常要映射成各自的对外提示，日志脱敏不在这层校验（各 Sender 自己负责）。
 */
@ExtendWith(MockitoExtension.class)
class VerifyCodeAppServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private VerifyCodeService verifyCodeService;

    @Mock
    private SmtpEmailSender smtpEmailSender;

    @Mock
    private AliyunSmsSender aliyunSmsSender;

    private VerifyCodeAppService verifyCodeAppService;

    @BeforeEach
    void setUp() {
        verifyCodeAppService = new VerifyCodeAppService();
        ReflectionTestUtils.setField(verifyCodeAppService, "userService", userService);
        ReflectionTestUtils.setField(verifyCodeAppService, "verifyCodeService", verifyCodeService);
        ReflectionTestUtils.setField(verifyCodeAppService, "smtpEmailSender", smtpEmailSender);
        ReflectionTestUtils.setField(verifyCodeAppService, "aliyunSmsSender", aliyunSmsSender);
    }

    private static UserSendCaptchaReq emailReq(String email, String type) {
        UserSendCaptchaReq req = new UserSendCaptchaReq();
        req.setEmail(email);
        req.setType(type);
        return req;
    }

    private static UserSendCaptchaReq telReq(String tel, String type) {
        UserSendCaptchaReq req = new UserSendCaptchaReq();
        req.setTel(tel);
        req.setType(type);
        return req;
    }

    // ==================== 邮箱验证码 ====================

    @Test
    void sendEmailCaptchaRejectsUnknownEmailOnForgetFlowWithoutGeneratingCode() {
        when(userService.getByEmail("nobody@example.com")).thenReturn(null);

        assertThatThrownBy(() -> verifyCodeAppService.sendEmailCaptcha(emailReq("nobody@example.com", "forget")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("该邮箱未注册");

        verify(verifyCodeService, never()).generateForAccount(anyString());
        verifyNoInteractions(smtpEmailSender);
    }

    @Test
    void sendEmailCaptchaRejectsInvalidEmailFormatWithoutGeneratingCode() {
        assertThatThrownBy(() -> verifyCodeAppService.sendEmailCaptcha(emailReq("not-an-email", "register")))
            .isInstanceOf(OperationFailedException.class)
            .hasMessage("邮箱格式不正确");

        verify(verifyCodeService, never()).generateForAccount(anyString());
        verifyNoInteractions(smtpEmailSender);
    }

    @Test
    void sendEmailCaptchaSucceedsAndPutsCodeInSubjectAndBody() {
        when(userService.getByEmail("alice@example.com")).thenReturn(new UserBO());
        when(verifyCodeService.generateForAccount("alice@example.com")).thenReturn("123456");
        when(smtpEmailSender.send(eq("alice@example.com"), anyString(), anyString(), eq("小智物联网管理平台")))
            .thenReturn(true);

        verifyCodeAppService.sendEmailCaptcha(emailReq("alice@example.com", "forget"));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(smtpEmailSender).send(eq("alice@example.com"), subjectCaptor.capture(), contentCaptor.capture(),
            eq("小智物联网管理平台"));
        assertThat(subjectCaptor.getValue()).isEqualTo("小智ESP32-智能物联网管理平台");
        assertThat(contentCaptor.getValue()).contains("123456");
    }

    @Test
    void sendEmailCaptchaThrowsWhenSenderReturnsFalse() {
        when(verifyCodeService.generateForAccount("alice@example.com")).thenReturn("123456");
        when(smtpEmailSender.send(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> verifyCodeAppService.sendEmailCaptcha(emailReq("alice@example.com", "register")))
            .isInstanceOf(OperationFailedException.class)
            .hasMessage("邮件发送失败，请检查邮箱配置");
    }

    @Test
    void sendEmailCaptchaThrowsGenericMessageWhenSenderThrows() {
        when(verifyCodeService.generateForAccount("alice@example.com")).thenReturn("123456");
        when(smtpEmailSender.send(anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("smtp broken"));

        assertThatThrownBy(() -> verifyCodeAppService.sendEmailCaptcha(emailReq("alice@example.com", "register")))
            .isInstanceOf(OperationFailedException.class)
            .hasMessage("发送失败，请稍后重试");
    }

    // ==================== 短信验证码 ====================

    @Test
    void sendSmsCaptchaRejectsUnknownTelOnForgetFlowWithoutGeneratingCode() {
        when(userService.getByTel("13800138000")).thenReturn(null);

        assertThatThrownBy(() -> verifyCodeAppService.sendSmsCaptcha(telReq("13800138000", "forget")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("该手机号未注册");

        verify(verifyCodeService, never()).generateForAccount(anyString());
        verifyNoInteractions(aliyunSmsSender);
    }

    @Test
    void sendSmsCaptchaRejectsInvalidTelFormatWithoutGeneratingCode() {
        assertThatThrownBy(() -> verifyCodeAppService.sendSmsCaptcha(telReq("12345", "register")))
            .isInstanceOf(OperationFailedException.class)
            .hasMessage("手机号格式不正确");

        verify(verifyCodeService, never()).generateForAccount(anyString());
        verifyNoInteractions(aliyunSmsSender);
    }

    @Test
    void sendSmsCaptchaSucceedsWithCorrectTelAndCode() {
        when(userService.getByTel("13800138000")).thenReturn(new UserBO());
        when(verifyCodeService.generateForAccount("13800138000")).thenReturn("654321");
        when(aliyunSmsSender.sendVerificationCode("13800138000", "654321")).thenReturn(true);

        verifyCodeAppService.sendSmsCaptcha(telReq("13800138000", "forget"));

        verify(aliyunSmsSender).sendVerificationCode("13800138000", "654321");
    }

    @Test
    void sendSmsCaptchaThrowsWhenSenderReturnsFalse() {
        when(verifyCodeService.generateForAccount("13800138000")).thenReturn("654321");
        when(aliyunSmsSender.sendVerificationCode(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> verifyCodeAppService.sendSmsCaptcha(telReq("13800138000", "register")))
            .isInstanceOf(OperationFailedException.class)
            .hasMessage("短信发送失败，请稍后重试");
    }

    @Test
    void sendSmsCaptchaThrowsGenericMessageWhenSenderThrows() {
        when(verifyCodeService.generateForAccount("13800138000")).thenReturn("654321");
        when(aliyunSmsSender.sendVerificationCode(anyString(), anyString()))
            .thenThrow(new RuntimeException("sms broken"));

        assertThatThrownBy(() -> verifyCodeAppService.sendSmsCaptcha(telReq("13800138000", "register")))
            .isInstanceOf(OperationFailedException.class)
            .hasMessage("短信发送失败，请联系管理员");
    }
}

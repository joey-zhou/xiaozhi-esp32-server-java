package com.xiaozhi.verifycode;

import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.model.req.UserSendCaptchaReq;
import com.xiaozhi.user.service.UserService;
import com.xiaozhi.verifycode.sender.AliyunSmsSender;
import com.xiaozhi.verifycode.sender.SmtpEmailSender;
import com.xiaozhi.verifycode.service.VerifyCodeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
/**
 * 验证码发送编排：邮箱/手机号格式与注册状态校验、生成验证码、投递到对应发送渠道。
 *
 * @author Joey
 */
@Slf4j
@Service
public class VerifyCodeAppService {

    private static final String FORGET = "forget";

    @Resource
    private UserService userService;

    @Resource
    private VerifyCodeService verifyCodeService;

    @Resource
    private SmtpEmailSender smtpEmailSender;

    @Resource
    private AliyunSmsSender aliyunSmsSender;

    /**
     * 发送邮箱验证码
     * <p>
     * 找回密码不暴露账号是否存在：账号不存在也照常返回、什么都不发；存在的放到后台发，
     * 发送耗时与失败提示都不会让两种情况看起来不一样。
     */
    public void sendEmailCaptcha(UserSendCaptchaReq req) {
        String email = req.getEmail();
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (FORGET.equals(req.getType())) {
            if (userService.getByEmail(email) != null) {
                String code = verifyCodeService.generateForAccount(email);
                Thread.startVirtualThread(() -> quietly(() -> deliverEmail(email, code)));
            }
            return;
        }
        deliverEmail(email, verifyCodeService.generateForAccount(email));
    }

    private void deliverEmail(String email, String code) {
        try {
            String subject = "小智ESP32-智能物联网管理平台";
            String content = "尊敬的用户您好!您的验证码为:<h3>" + code + "</h3>如不是您操作,请忽略此邮件.(有效期10分钟)";
            boolean success = smtpEmailSender.send(email, subject, content, "小智物联网管理平台");
            if (!success) {
                log.error("邮箱验证码发送失败: {}", maskEmail(email));
                throw new OperationFailedException("邮件发送失败，请检查邮箱配置");
            }
            log.info("邮箱验证码发送成功: {}", maskEmail(email));
        } catch (OperationFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("发送邮箱验证码异常: {}", e.getMessage(), e);
            throw new OperationFailedException("发送失败，请稍后重试");
        }
    }

    /**
     * 发送短信验证码
     * <p>
     * 找回密码的处理同 {@link #sendEmailCaptcha}。
     */
    public void sendSmsCaptcha(UserSendCaptchaReq req) {
        String tel = req.getTel();
        if (!StringUtils.hasText(tel)) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        if (FORGET.equals(req.getType())) {
            if (userService.getByTel(tel) != null) {
                String code = verifyCodeService.generateForAccount(tel);
                Thread.startVirtualThread(() -> quietly(() -> deliverSms(tel, code)));
            }
            return;
        }
        deliverSms(tel, verifyCodeService.generateForAccount(tel));
    }

    private void deliverSms(String tel, String code) {
        try {
            boolean success = aliyunSmsSender.sendVerificationCode(tel, code);
            if (!success) {
                log.error("短信验证码发送失败: {}", maskPhone(tel));
                throw new OperationFailedException("短信发送失败，请稍后重试");
            }
            log.info("短信验证码发送成功: {}", maskPhone(tel));
        } catch (OperationFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("发送短信验证码异常: {}", e.getMessage(), e);
            throw new OperationFailedException("短信发送失败，请联系管理员");
        }
    }

    /** 后台发送的失败只记日志：回给调用方就等于告诉他这个账号存在 */
    private static void quietly(Runnable delivery) {
        try {
            delivery.run();
        } catch (RuntimeException e) {
            log.warn("找回密码验证码后台发送失败: {}", e.getMessage());
        }
    }

    /**
     * 日志脱敏：邮箱只保留 @ 前最多 2 位，其余用 *** 代替
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    /**
     * 日志脱敏：手机号只保留前 3 位和后 4 位，中间用 **** 代替
     */
    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return "***";
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}

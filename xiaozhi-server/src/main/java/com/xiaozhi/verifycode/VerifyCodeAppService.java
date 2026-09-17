package com.xiaozhi.verifycode;

import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.model.req.UserSendCaptchaReq;
import com.xiaozhi.user.service.UserService;
import com.xiaozhi.verifycode.sender.AliyunSmsSender;
import com.xiaozhi.verifycode.sender.SmtpEmailSender;
import com.xiaozhi.verifycode.service.VerifyCodeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
/**
 * 验证码发送编排：邮箱/手机号格式与注册状态校验、生成验证码、投递到对应发送渠道。
 *
 * @author Joey
 */
@Slf4j
@Service
public class VerifyCodeAppService {

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
     * 格式校验放在生成验证码之前，避免为非法地址生成验证码。
     */
    public void sendEmailCaptcha(UserSendCaptchaReq req) {
        String email = req.getEmail();
        if ("forget".equals(req.getType()) && userService.getByEmail(email) == null) {
            throw new IllegalArgumentException("该邮箱未注册");
        }
        if (!isValidEmail(email)) {
            throw new OperationFailedException("邮箱格式不正确");
        }

        String code = verifyCodeService.generateForAccount(email);
        // 验证码格式不对也不能把验证码原文打进日志
        if (!isValidCode(code)) {
            log.warn("验证码格式不正确");
            throw new OperationFailedException("验证码格式不正确");
        }

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
     * 格式校验放在生成验证码之前，避免为非法号码生成验证码。
     */
    public void sendSmsCaptcha(UserSendCaptchaReq req) {
        String tel = req.getTel();
        if ("forget".equals(req.getType()) && userService.getByTel(tel) == null) {
            throw new IllegalArgumentException("该手机号未注册");
        }
        if (!isValidPhoneNumber(tel)) {
            throw new OperationFailedException("手机号格式不正确");
        }

        String code = verifyCodeService.generateForAccount(tel);
        // 验证码格式不对也不能把验证码原文打进日志
        if (!isValidCode(code)) {
            log.warn("验证码格式不正确");
            throw new OperationFailedException("验证码格式不正确");
        }

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

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[^@]+@[^@]+\\.[^@]+$");
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("^1\\d{10}$");
    }

    private boolean isValidCode(String code) {
        return code != null && code.matches("^[0-9A-Za-z]{4,6}$");
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

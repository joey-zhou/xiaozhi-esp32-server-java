package com.xiaozhi.utils;

import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
/**
 * 验证码发送工具类
 * 统一管理邮件和短信验证码发送
 * 
 * @author Joey
 */
@Slf4j
@Component
public class CaptchaUtils {
    
    @Resource
    private EmailUtils emailUtils;
    
    @Resource
    private SmsUtils smsUtils;
    
    /**
     * 验证码类型枚举
     */
    public enum CaptchaType {
        EMAIL("邮箱"),
        SMS("短信");
        
        private final String description;
        
        CaptchaType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 验证码发送结果
     */
    public static class CaptchaResult {
        private boolean success;
        private String message;
        
        public CaptchaResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public static CaptchaResult success() {
            return new CaptchaResult(true, "发送成功");
        }
        
        public static CaptchaResult error(String message) {
            return new CaptchaResult(false, message);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * 发送邮箱验证码
     * 
     * @param email 邮箱地址
     * @param code 验证码
     * @return 发送结果
     */
    public CaptchaResult sendEmailCaptcha(String email, String code) {
        try {
            // 验证邮箱格式
            if (!isValidEmail(email)) {
                log.warn("邮箱格式不正确: {}", maskEmail(email));
                return CaptchaResult.error("邮箱格式不正确");
            }

            // 验证验证码：格式不对也不能把验证码原文打进日志
            if (!isValidCode(code)) {
                log.warn("验证码格式不正确");
                return CaptchaResult.error("验证码格式不正确");
            }

            // 发送邮件
            boolean success = emailUtils.sendCaptchaEmail(email, code);

            if (success) {
                log.info("邮箱验证码发送成功: {}", maskEmail(email));
                return CaptchaResult.success();
            } else {
                log.error("邮箱验证码发送失败: {}", maskEmail(email));
                return CaptchaResult.error("邮件发送失败，请检查邮箱配置");
            }

        } catch (Exception e) {
            log.error("发送邮箱验证码异常: {}", e.getMessage(), e);
            return CaptchaResult.error("发送失败，请稍后重试");
        }
    }
    
    /**
     * 发送短信验证码
     * 
     * @param phoneNumber 手机号
     * @param code 验证码
     * @return 发送结果
     */
    public CaptchaResult sendSmsCaptcha(String phoneNumber, String code) {
        try {
            // 验证手机号格式
            if (!isValidPhoneNumber(phoneNumber)) {
                log.warn("手机号格式不正确: {}", maskPhone(phoneNumber));
                return CaptchaResult.error("手机号格式不正确");
            }

            // 验证验证码：格式不对也不能把验证码原文打进日志
            if (!isValidCode(code)) {
                log.warn("验证码格式不正确");
                return CaptchaResult.error("验证码格式不正确");
            }

            // 发送短信
            boolean success = smsUtils.sendVerificationCodeSms(phoneNumber, code);

            if (success) {
                log.info("短信验证码发送成功: {}", maskPhone(phoneNumber));
                return CaptchaResult.success();
            } else {
                log.error("短信验证码发送失败: {}", maskPhone(phoneNumber));
                return CaptchaResult.error("短信发送失败，请稍后重试");
            }

        } catch (Exception e) {
            log.error("发送短信验证码异常: {}", e.getMessage(), e);
            return CaptchaResult.error("短信发送失败，请联系管理员");
        }
    }
    
    /**
     * 验证邮箱格式
     * 
     * @param email 邮箱地址
     * @return 是否有效
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // 邮箱格式验证：包含@符号且@后面有.
        return email.matches("^[^@]+@[^@]+\\.[^@]+$");
    }
    
    /**
     * 验证手机号格式
     * 
     * @param phoneNumber 手机号
     * @return 是否有效
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        // 中国大陆手机号格式验证：11位数字，以1开头
        return phoneNumber.matches("^1\\d{10}$");
    }
    
    /**
     * 验证验证码格式
     * 
     * @param code 验证码
     * @return 是否有效
     */
    private boolean isValidCode(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        // 验证码通常是4-6位数字或字母
        return code.matches("^[0-9A-Za-z]{4,6}$");
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


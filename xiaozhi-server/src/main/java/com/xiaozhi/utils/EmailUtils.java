package com.xiaozhi.utils;

import io.github.biezhi.ome.OhMyEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static io.github.biezhi.ome.OhMyEmail.SMTP_QQ;

import lombok.extern.slf4j.Slf4j;
/**
 * 邮件发送工具类
 * 
 * @author Joey
 */
@Slf4j
@Component
public class EmailUtils {
    
    @Value("${email.smtp.username}")
    private String emailUsername;

    @Value("${email.smtp.password}")
    private String emailPassword;
    
    /**
     * 发送邮件
     * 
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @return 发送结果
     */
    public boolean sendEmail(String to, String subject, String content) {
        return sendEmail(to, subject, content, "小智物联网管理平台");
    }
    
    /**
     * 发送邮件
     * 
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @param fromName 发件人名称
     * @return 发送结果
     */
    public boolean sendEmail(String to, String subject, String content, String fromName) {
        try {
            // 验证邮箱格式
            if (!isValidEmail(to)) {
                log.error("邮箱格式不正确: {}", maskEmail(to));
                return false;
            }
            
            // 检查邮箱配置
            if (!StringUtils.hasText(emailUsername) || !StringUtils.hasText(emailPassword)) {
                log.error("未配置第三方邮箱认证信息");
                return false;
            }
            
            // 配置邮件发送
            OhMyEmail.config(SMTP_QQ(false), emailUsername, emailPassword);
            
            // 发送邮件
            OhMyEmail.subject(subject)
                    .from(fromName)
                    .to(to)
                    .html(content)
                    .send();
            
            log.info("邮件发送成功: {} -> {}", fromName, maskEmail(to));
            return true;

        } catch (Exception e) {
            String errorMsg = getErrorMessage(e);
            log.error("邮件发送失败: {} -> {}, 错误: {}", fromName, maskEmail(to), errorMsg, e);
            return false;
        }
    }
    
    /**
     * 发送验证码邮件
     * 
     * @param to 收件人邮箱
     * @param code 验证码
     * @return 发送结果
     */
    public boolean sendCaptchaEmail(String to, String code) {
        String subject = "小智ESP32-智能物联网管理平台";
        String content = "尊敬的用户您好!您的验证码为:<h3>" + code + "</h3>如不是您操作,请忽略此邮件.(有效期10分钟)";
        return sendEmail(to, subject, content);
    }
    
    /**
     * 简单验证邮箱格式
     * 
     * @param email 邮箱地址
     * @return 是否有效
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // 简单的邮箱格式验证，包含@符号且@后面有.
        return email.matches("^[^@]+@[^@]+\\.[^@]+$");
    }
    
    /**
     * 根据异常类型获取错误信息
     * 
     * @param e 异常
     * @return 错误信息
     */
    private String getErrorMessage(Exception e) {
        if (e.getMessage() == null) {
            return "发送失败";
        }
        
        String message = e.getMessage();
        if (message.contains("non-existent account") ||
                message.contains("550") ||
                message.contains("recipient")) {
            return "邮箱地址不存在或无效";
        } else if (message.contains("Authentication failed")) {
            return "邮箱服务认证失败，请联系管理员";
        } else if (message.contains("timed out")) {
            return "邮件发送超时，请稍后重试";
        }
        
        return "发送失败: " + message;
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
}

package com.xiaozhi.utils;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 短信工具类
 * 
 * @author Joey
 */
@Component
public class SmsUtils {
    private static final Logger logger = LoggerFactory.getLogger(SmsUtils.class);

    @Value("${sms.aliyun.access-key-id:}")
    private String accessKeyId;

    @Value("${sms.aliyun.access-key-secret:}")
    private String accessKeySecret;

    @Value("${sms.aliyun.sign-name:}")
    private String signName;

    @Value("${sms.aliyun.template-code:}")
    private String templateCode;

    /**
     * 发送验证码短信
     * 
     * @param phoneNumber 手机号
     * @param verificationCode 验证码
     * @return 是否发送成功
     */
    public boolean sendVerificationCodeSms(String phoneNumber, String verificationCode) {
        try {
            // 创建Aliyun客户端
            Client client = createClient();
            
            // 构建短信请求
            SendSmsRequest sendSmsRequest = new SendSmsRequest()
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setPhoneNumbers(phoneNumber)
                .setTemplateParam(String.format("{\"code\":\"%s\"}", verificationCode));
            
            // 发送短信
            RuntimeOptions runtime = new RuntimeOptions();
            SendSmsResponse sendSmsResponse = client.sendSmsWithOptions(sendSmsRequest, runtime);
            
            // 记录请求ID
            logger.info("发送短信响应的requestID: {}", sendSmsResponse.getBody().getRequestId());
            
            // 检查发送结果
            String code = sendSmsResponse.getBody().getCode();
            if ("OK".equals(code)) {
                logger.info("短信发送成功，手机号: {}", phoneNumber);
                return true;
            } else {
                logger.error("短信发送失败，错误码: {}, 错误信息: {}", code, sendSmsResponse.getBody().getMessage());
                return false;
            }
        } catch (Exception e) {
            logger.error("发送短信异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 创建阿里云短信客户端
     * 
     * @return 阿里云客户端
     * @throws Exception 如果创建失败
     */
    private Client createClient() throws Exception {
        Config config = new Config()
            .setAccessKeyId(accessKeyId)
            .setAccessKeySecret(accessKeySecret);
        
        // 配置 Endpoint
        config.endpoint = "dysmsapi.aliyuncs.com";
        return new Client(config);
    }
}
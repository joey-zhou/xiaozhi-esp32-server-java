package com.xiaozhi.verifycode.sender;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
/**
 * 阿里云短信发送
 *
 * @author Joey
 */
@Slf4j
@Component
public class AliyunSmsSender {
    @Value("${sms.aliyun.access-key-id:}")
    private String accessKeyId;

    @Value("${sms.aliyun.access-key-secret:}")
    private String accessKeySecret;

    @Value("${sms.aliyun.sign-name:}")
    private String signName;

    @Value("${sms.aliyun.template-code:}")
    private String templateCode;

    private volatile Client smsClient;

    /**
     * 发送验证码短信
     *
     * @param phoneNumber 手机号
     * @param code 验证码
     * @return 是否发送成功
     */
    public boolean sendVerificationCode(String phoneNumber, String code) {
        try {
            // 创建Aliyun客户端
            Client client = getOrCreateClient();

            // 构建短信请求
            SendSmsRequest sendSmsRequest = new SendSmsRequest()
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setPhoneNumbers(phoneNumber)
                .setTemplateParam(String.format("{\"code\":\"%s\"}", code));

            // 发送短信
            RuntimeOptions runtime = new RuntimeOptions();
            SendSmsResponse sendSmsResponse = client.sendSmsWithOptions(sendSmsRequest, runtime);

            // 记录请求ID
            log.info("发送短信响应的requestID: {}", sendSmsResponse.getBody().getRequestId());

            // 检查发送结果
            String resultCode = sendSmsResponse.getBody().getCode();
            if ("OK".equals(resultCode)) {
                log.info("短信发送成功，手机号: {}", maskPhone(phoneNumber));
                return true;
            } else {
                log.error("短信发送失败，错误码: {}, 错误信息: {}", resultCode, sendSmsResponse.getBody().getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("发送短信异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 取阿里云短信客户端：Client 无外部状态且线程安全，进程内建一次后复用；
     * 懒加载而不是启动时构造，短信密钥没配也不影响应用启动
     *
     * @return 阿里云客户端
     * @throws Exception 如果创建失败
     */
    private Client getOrCreateClient() throws Exception {
        Client local = smsClient;
        if (local == null) {
            synchronized (this) {
                local = smsClient;
                if (local == null) {
                    Config config = new Config()
                        .setAccessKeyId(accessKeyId)
                        .setAccessKeySecret(accessKeySecret);

                    // 配置 Endpoint
                    config.endpoint = "dysmsapi.aliyuncs.com";
                    local = new Client(config);
                    smsClient = local;
                }
            }
        }
        return local;
    }

    /**
     * 日志脱敏：手机号只保留前 3 位和后 4 位，中间用 **** 代替
     */
    static String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return "***";
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}

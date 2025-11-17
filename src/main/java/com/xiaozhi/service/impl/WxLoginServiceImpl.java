package com.xiaozhi.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.service.WxLoginService;
import com.xiaozhi.utils.HttpUtil;
import com.xiaozhi.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信登录服务实现
 */
@Service
public class WxLoginServiceImpl implements WxLoginService {
    
    @Value("${wechat.appid:}")
    private String appid;
    
    @Value("${wechat.secret:}")
    private String secret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public Map<String, String> getWxLoginInfo(String code) {
        // 微信小程序登录API地址
        String url = "https://api.weixin.qq.com/sns/jscode2session";
        
        // 构建完整URL，包含查询参数
        String fullUrl = String.format("%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                url, appid, secret, code);
        
        // 发送请求
        String response = restTemplate.getForObject(fullUrl, String.class);
        
        // 解析响应
        Map<String, String> result = new HashMap<>();
        try {
            // 使用Jackson解析JSON
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            System.out.println(responseMap.toString());
            if (responseMap.containsKey("openid")) {
                result.put("openid", (String) responseMap.get("openid"));
                result.put("session_key", (String) responseMap.get("session_key"));
                
                // 如果有unionid，也保存下来
                if (responseMap.containsKey("unionid")) {
                    result.put("unionid", (String) responseMap.get("unionid"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("解析微信登录响应失败", e);
        }
        
        return result;
    }
}
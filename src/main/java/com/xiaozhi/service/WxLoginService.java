package com.xiaozhi.service;

import java.util.Map;

/**
 * 微信登录服务接口
 */
public interface WxLoginService {
    
    /**
     * 获取微信登录信息
     * 
     * @param code 微信登录code
     * @return 包含openid和session_key的Map
     */
    Map<String, String> getWxLoginInfo(String code);
}
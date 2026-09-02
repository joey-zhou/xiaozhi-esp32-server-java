package com.xiaozhi.communication.server.websocket;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.communication.auth.DeviceAuthService;
import com.xiaozhi.utils.CommonUtils;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * WS 握手鉴权：MAC 设备验 OTA 签发的 HMAC token，其余（web 端 user_chat_*）验管理端登录态。
 * 凭据取 Authorization 头（Bearer 前缀可选），浏览器无法设头时取 query 参数 token。
 */
@Slf4j
@Component
public class DeviceAuthHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private DeviceAuthService deviceAuthService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!deviceAuthService.isEnabled()) {
            return true;
        }
        Map<String, String> params = parseQuery(request.getURI().getRawQuery());
        String deviceId = firstNonBlank(request.getHeaders().getFirst("device-id"),
                params.get("device-id"));
        String token = stripBearer(request.getHeaders().getFirst("Authorization"));
        if (!StringUtils.hasText(token)) {
            token = stripBearer(firstNonBlank(params.get("token"), params.get("Authorization")));
        }

        if (!StringUtils.hasText(deviceId)) {
            return reject(response, "缺少device-id", null);
        }
        if (deviceAuthService.isAllowedDevice(deviceId)) {
            return true;
        }
        if (!StringUtils.hasText(token)) {
            return reject(response, "缺少token", deviceId);
        }
        if (CommonUtils.isMacAddressValid(deviceId)) {
            if (deviceAuthService.verifyDeviceToken(token, deviceId)) {
                return true;
            }
            return reject(response, "设备token无效或已过期", deviceId);
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId != null) {
                // web 会话身份必须与登录用户一致
                if (deviceId.startsWith("user_chat_") && !deviceId.equals("user_chat_" + loginId)) {
                    return reject(response, "登录用户与会话身份不一致", deviceId);
                }
                return true;
            }
        } catch (RuntimeException e) {
            log.debug("登录态校验异常 - DeviceId: {}", deviceId, e);
        }
        return reject(response, "登录token无效", deviceId);
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private boolean reject(ServerHttpResponse response, String reason, String deviceId) {
        log.warn("WS握手鉴权失败: {} - DeviceId: {}", reason, deviceId);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    private static String stripBearer(String value) {
        if (value != null && value.startsWith("Bearer ")) {
            return value.substring(7);
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (!StringUtils.hasText(rawQuery)) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return params;
    }
}

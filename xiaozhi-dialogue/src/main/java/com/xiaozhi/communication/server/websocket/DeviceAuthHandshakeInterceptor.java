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
 *
 * 设备身份只在这里判定一次，判定结果写进握手属性 {@link #ATTR_DEVICE_ID}，
 * 连接建立之后一律只从该属性取，不得再从请求头或 URI 查询参数二次解析。
 */
@Slf4j
@Component
public class DeviceAuthHandshakeInterceptor implements HandshakeInterceptor {

    /** 握手属性键：本连接被判定的设备标识 */
    public static final String ATTR_DEVICE_ID = "deviceId";

    @Resource
    private DeviceAuthService deviceAuthService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Map<String, String> params = parseQuery(request.getURI().getRawQuery());
        String deviceId = firstNonBlank(request.getHeaders().getFirst("device-id"),
                params.get("device-id"));
        if (!deviceAuthService.isEnabled()) {
            return accept(attributes, deviceId);
        }
        String token = stripBearer(request.getHeaders().getFirst("Authorization"));
        if (!StringUtils.hasText(token)) {
            token = stripBearer(firstNonBlank(params.get("token"), params.get("Authorization")));
        }

        if (!StringUtils.hasText(deviceId)) {
            return reject(response, "缺少device-id", null);
        }
        if (deviceAuthService.isAllowedDevice(deviceId)) {
            return accept(attributes, deviceId);
        }
        if (!StringUtils.hasText(token)) {
            return reject(response, "缺少token", deviceId);
        }
        if (CommonUtils.isMacAddressValid(deviceId)) {
            if (deviceAuthService.verifyDeviceToken(token, deviceId)) {
                return accept(attributes, deviceId);
            }
            return reject(response, "设备token无效或已过期", deviceId);
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId != null) {
                // 非 MAC 的会话身份必须与登录用户逐字一致；大小写变形会被库排序规则当成同一行
                if (!deviceId.equals("user_chat_" + loginId)) {
                    return reject(response, "登录用户与会话身份不一致", deviceId);
                }
                return accept(attributes, deviceId);
            }
        } catch (RuntimeException e) {
            log.debug("登录态校验异常 - DeviceId: {}", deviceId, e);
        }
        return reject(response, "登录token无效", deviceId);
    }

    /** 放行并把判定出的设备标识写进握手属性，供连接建立阶段取用 */
    private static boolean accept(Map<String, Object> attributes, String deviceId) {
        if (StringUtils.hasText(deviceId)) {
            attributes.put(ATTR_DEVICE_ID, deviceId);
        }
        return true;
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

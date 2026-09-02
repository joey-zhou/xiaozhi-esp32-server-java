package com.xiaozhi.communication.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.extern.slf4j.Slf4j;

/**
 * 设备接入 token 的签发与校验（HMAC-SHA256，无状态）。
 *
 * 设备 token：OTA 下发，格式 {@code base64url(hmac(deviceId|ts)).ts}，握手时用明文 deviceId 重签比对。
 * 视觉 token：MCP initialize 下发，格式 {@code base64url(sessionId|deviceId|exp).base64url(hmac(payload))}，
 * 设备调用视觉接口时原样回显，服务端签发与校验闭环，不依赖 enabled 开关。
 */
@Slf4j
@Component
public class DeviceAuthService {

    @Value("${xiaozhi.device-auth.secret:}")
    private String secret;

    @Value("${xiaozhi.device-auth.expire-seconds:2592000}")
    private long expireSeconds;

    @Value("${xiaozhi.device-auth.allowed-devices:}")
    private String allowedDevicesConfig;

    private Set<String> allowedDevices = Set.of();

    /** 视觉 token 必须始终可签发；未配置 secret 时退化为进程内随机密钥 */
    private String visionSecret;

    @PostConstruct
    void init() {
        if (!StringUtils.hasText(secret)) {
            log.warn("未配置 xiaozhi.device-auth.secret，设备接入鉴权未启用");
        }
        visionSecret = StringUtils.hasText(secret) ? secret : UUID.randomUUID().toString();
        if (StringUtils.hasText(allowedDevicesConfig)) {
            allowedDevices = Arrays.stream(allowedDevicesConfig.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        }
    }

    /** 配置了 secret 即开启握手鉴权 */
    public boolean isEnabled() {
        return StringUtils.hasText(secret);
    }

    /** 白名单设备免 token 接入 */
    public boolean isAllowedDevice(String deviceId) {
        return deviceId != null && allowedDevices.contains(deviceId.toLowerCase(Locale.ROOT));
    }

    /** 未配置 secret 时返回空串（保持 OTA 原有行为） */
    public String generateDeviceToken(String deviceId) {
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(deviceId)) {
            return "";
        }
        long ts = System.currentTimeMillis() / 1000;
        return sign(normalize(deviceId) + "|" + ts, secret) + "." + ts;
    }

    public boolean verifyDeviceToken(String token, String deviceId) {
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(token) || !StringUtils.hasText(deviceId)) {
            return false;
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(token.substring(dot + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        // 300s 容忍设备与服务端时钟偏差
        if (now - ts > expireSeconds || ts - now > 300) {
            return false;
        }
        String expected = sign(normalize(deviceId) + "|" + ts, secret);
        return constantTimeEquals(token.substring(0, dot), expected);
    }

    public String generateVisionToken(String sessionId, String deviceId) {
        long exp = System.currentTimeMillis() / 1000 + expireSeconds;
        // 字段逐个转义再拼接，会话号或设备号里含分隔符时载荷不会被切错段
        String payload = encodeField(sessionId) + "|"
                + encodeField(deviceId == null ? "" : normalize(deviceId)) + "|" + exp;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + sign(payload, visionSecret);
    }

    private static String encodeField(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decodeField(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** 校验通过返回载荷，否则返回 null */
    public VisionToken verifyVisionToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return null;
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!constantTimeEquals(token.substring(dot + 1), sign(payload, visionSecret))) {
            return null;
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            if (Long.parseLong(parts[2]) < System.currentTimeMillis() / 1000) {
                return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        try {
            return new VisionToken(decodeField(parts[0]), decodeField(parts[1]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record VisionToken(String sessionId, String deviceId) {
    }

    private static String normalize(String deviceId) {
        return deviceId.trim().toLowerCase(Locale.ROOT);
    }

    private static String sign(String content, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256签名失败", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}

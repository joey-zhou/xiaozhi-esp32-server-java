package com.xiaozhi.common.web;

import com.xiaozhi.utils.RequestContextUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * 可信代理判定。
 * <p>
 * 限流、封禁、登录审计等安全用途必须用本类取客户端 IP，不能直接读代理头。
 * xiaozhi.security.trusted-proxies 配置反向代理的 IP 或 CIDR（如 127.0.0.1、10.0.0.0/8）：
 * 未配置时只认 TCP 对端地址，任何 X-Forwarded-For 都不采信；
 * 配置后仅当对端命中列表时才解析 X-Forwarded-For，取最右侧那一跳非可信代理的地址。
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "xiaozhi.security")
public class TrustedProxyPolicy {

    /** 可信反向代理的 IP 或 CIDR 列表 */
    private List<String> trustedProxies = new ArrayList<>();

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? new ArrayList<>() : trustedProxies;
    }

    @PostConstruct
    public void logConfiguration() {
        if (trustedProxies.isEmpty()) {
            log.info("未配置 xiaozhi.security.trusted-proxies，安全用途的客户端 IP 只取 TCP 对端地址");
            return;
        }
        for (String entry : trustedProxies) {
            if (!RequestContextUtils.isIpLiteral(networkPartOf(entry))) {
                log.warn("xiaozhi.security.trusted-proxies 配置项不是合法的 IP/CIDR，将被忽略: {}", entry);
            }
        }
        log.info("可信代理已配置 - {}", trustedProxies);
    }

    /** 取 CIDR 里斜杠之前的网络地址部分，无斜杠时就是整串 */
    private static String networkPartOf(String entry) {
        if (entry == null) {
            return "";
        }
        int slash = entry.indexOf('/');
        return (slash < 0 ? entry : entry.substring(0, slash)).trim();
    }

    /**
     * 取用于安全判定的客户端 IP
     */
    public String resolveClientIp(HttpServletRequest request) {
        return RequestContextUtils.getTrustedClientIp(request, trustedProxies);
    }

    /**
     * 从当前请求上下文取用于安全判定的客户端 IP，无请求上下文时返回 null
     */
    public String resolveClientIp() {
        return RequestContextUtils.getTrustedClientIp(trustedProxies);
    }
}

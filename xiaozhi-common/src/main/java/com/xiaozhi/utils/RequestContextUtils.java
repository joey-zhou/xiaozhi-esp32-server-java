package com.xiaozhi.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 请求上下文工具类
 * 提供从 Spring RequestContextHolder 获取客户端 IP 的方法
 *
 * @author Joey
 */
public class RequestContextUtils {

    /** IPv4 字面量 */
    private static final Pattern IPV4_LITERAL = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$");

    /** IPv6 字面量（十六进制段 + 至少一个冒号，允许 :: 压缩，最终由 InetAddress 校验） */
    private static final Pattern IPV6_LITERAL = Pattern.compile(
            "^[0-9A-Fa-f]{0,4}(?::[0-9A-Fa-f]{0,4}){1,7}$");

    /**
     * 获取客户端真实 IP 地址
     * 优先从代理头（X-Forwarded-For、X-Real-IP 等）获取，最后回退到 RemoteAddr
     * <p>
     * 代理头由客户端任意伪造，返回值只能用于日志展示，不能用于限流、封禁等安全判定，
     * 安全用途请用 {@link #getTrustedClientIp(HttpServletRequest, List)}。
     *
     * @return 客户端 IP 地址
     */
    public static String getClientIp() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
            return getClientIp(request);
        }
        return null;
    }

    /**
     * 从 HttpServletRequest 获取客户端真实 IP 地址
     * <p>
     * 代理头由客户端任意伪造，返回值只能用于日志展示，不能用于限流、封禁等安全判定，
     * 安全用途请用 {@link #getTrustedClientIp(HttpServletRequest, List)}。
     *
     * @param request HTTP 请求对象
     * @return 客户端 IP 地址
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index).trim();
            } else {
                return ip.trim();
            }
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        ip = request.getHeader("HTTP_CLIENT_IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * 获取可用于安全判定（限流、登录审计）的客户端 IP。
     * <p>
     * trustedProxies 为空时只返回 TCP 对端地址，不采信任何代理头；
     * 配置了可信代理且对端命中时，才解析 X-Forwarded-For，从右往左取第一个
     * 「合法 IP 字面量且不在可信网段内」的值，其余代理头一律不采信。
     *
     * @param request        HTTP 请求对象
     * @param trustedProxies 可信代理的 IP 或 CIDR 列表，可为 null/空
     * @return 客户端 IP 地址
     */
    public static String getTrustedClientIp(HttpServletRequest request, List<String> trustedProxies) {
        if (request == null) {
            return null;
        }
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies == null || trustedProxies.isEmpty() || !isTrustedProxy(remoteAddr, trustedProxies)) {
            return remoteAddr;
        }

        List<String> hops = forwardedForHops(request);
        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = hops.get(i);
            if (!isIpLiteral(hop)) {
                // 出现伪造或畸形的一跳，右侧之外的内容都不可信
                return remoteAddr;
            }
            if (!isTrustedProxy(hop, trustedProxies)) {
                return hop;
            }
        }
        return remoteAddr;
    }

    /**
     * 按到达顺序取出所有 X-Forwarded-For 跳；同名头可能出现多次，必须全部读出而不是只取第一行
     */
    private static List<String> forwardedForHops(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders("X-Forwarded-For");
        if (values == null) {
            return List.of();
        }
        List<String> hops = new ArrayList<>();
        while (values.hasMoreElements()) {
            String value = values.nextElement();
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String hop : value.split(",")) {
                hops.add(hop.trim());
            }
        }
        return hops;
    }

    /**
     * 从当前请求上下文获取可用于安全判定的客户端 IP
     */
    public static String getTrustedClientIp(List<String> trustedProxies) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            return getTrustedClientIp(((ServletRequestAttributes) requestAttributes).getRequest(), trustedProxies);
        }
        return null;
    }

    /**
     * 判断字符串是否为合法的 IPv4/IPv6 字面量（不做任何 DNS 解析）
     */
    public static boolean isIpLiteral(String value) {
        return toAddressBytes(value) != null;
    }

    /**
     * 判断 IP 是否落在可信代理列表内，列表项为 IP 字面量或 CIDR
     */
    private static boolean isTrustedProxy(String ip, List<String> trustedProxies) {
        byte[] address = toAddressBytes(ip);
        if (address == null) {
            return false;
        }
        for (String entry : trustedProxies) {
            if (matchesCidr(address, entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCidr(byte[] address, String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        String candidate = entry.trim();
        int slash = candidate.indexOf('/');
        String network = slash < 0 ? candidate : candidate.substring(0, slash);
        byte[] networkBytes = toAddressBytes(network);
        if (networkBytes == null || networkBytes.length != address.length) {
            return false;
        }

        int prefixBits = networkBytes.length * 8;
        if (slash >= 0) {
            try {
                prefixBits = Integer.parseInt(candidate.substring(slash + 1).trim());
            } catch (NumberFormatException e) {
                return false;
            }
            if (prefixBits < 0 || prefixBits > networkBytes.length * 8) {
                return false;
            }
        }

        int fullBytes = prefixBits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (address[i] != networkBytes[i]) {
                return false;
            }
        }
        int remainingBits = prefixBits % 8;
        if (remainingBits > 0) {
            int mask = 0xFF << (8 - remainingBits);
            return (address[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
        }
        return true;
    }

    /**
     * 把 IP 字面量转成字节，非字面量（含主机名）一律返回 null，避免触发 DNS 解析
     */
    private static byte[] toAddressBytes(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String candidate = ip.trim();
        // IPv4-mapped IPv6（::ffff:a.b.c.d）统一按 IPv4 处理
        if (candidate.regionMatches(true, 0, "::ffff:", 0, 7)) {
            candidate = candidate.substring(7);
        }
        if (!IPV4_LITERAL.matcher(candidate).matches() && !IPV6_LITERAL.matcher(candidate).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}

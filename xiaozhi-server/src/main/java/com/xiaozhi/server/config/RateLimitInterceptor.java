package com.xiaozhi.server.config;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.common.web.TrustedProxyPolicy;

import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
/**
 * 登录限流拦截器
 * <p>
 * 对登录、注册、验证码、账号存在性查询、配置试拨、设备绑定、OTA 等端点按请求主体做频率限制，
 * 超出后返回 429 (Too Many Requests)。
 * <p>
 * 限流主体：已登录请求按用户 ID 计数，OTA 按 Device-Id 计数，其余按客户端 IP 计数；
 * 匿名的账号类端点再叠加一维「账号」（用户名/手机号/邮箱），两维任一超限即拒——
 * 只按 IP 挡不住换 IP 的分布式爆破，只按账号挡不住撞库。
 * 账号取自请求体，由 {@link AccountSubjectFilter} 提前解析并挂到请求属性上。
 * <p>
 * IP 取自 TCP 对端地址，只有配置了 {@code xiaozhi.security.trusted-proxies} 且对端命中可信网段时
 * 才解析 X-Forwarded-For——否则任意客户端改一个请求头就能换一个限流 key。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** Redis key 前缀 */
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    /** 请求体里解析出的账号，挂在请求属性上传给拦截器 */
    static final String ACCOUNT_ATTRIBUTE = "xiaozhi.rateLimit.account";

    /** 时间窗口（秒） */
    private static final int WINDOW_SECONDS = 60;

    /** 登录/注册端点：每分钟最多 10 次 */
    private static final int MAX_AUTH_REQUESTS = 10;

    /** 验证码端点：每分钟最多 5 次 */
    private static final int MAX_CAPTCHA_REQUESTS = 5;

    /** 设备绑定端点：绑定码只有 6 位，必须比登录更严，每分钟最多 5 次 */
    private static final int MAX_BIND_REQUESTS = 5;

    /** 账号存在性查询：注册一次只查一次，够用即可，宽了就成了枚举通道 */
    private static final int MAX_CHECK_USER_REQUESTS = 10;

    /** 配置试拨：每次都是一次计费外呼，按人来回改参数试的节奏留量 */
    private static final int MAX_CONFIG_TEST_REQUESTS = 20;

    /** OTA 端点按设备计数：设备每次开机只需请求一次，留 10 倍余量 */
    private static final int MAX_OTA_DEVICE_REQUESTS = 10;

    /** OTA 端点按 IP 计数：整片设备可能在同一个 NAT 出口后面，阈值放宽到能拦住 MAC 枚举即可 */
    private static final int MAX_OTA_IP_REQUESTS = 60;

    /** OTA 激活状态是设备轮询端点（202 表示继续等待），阈值必须容得下轮询节奏与同一出口的多台设备 */
    private static final int MAX_OTA_POLL_DEVICE_REQUESTS = 60;

    private static final int MAX_OTA_POLL_IP_REQUESTS = 600;

    /**
     * 计数与设置过期必须在一次 Redis 调用里完成：分两步时进程在两步之间挂掉会留下永不过期的 key，
     * 把该主体永久锁死。TTL 为负说明是历史遗留或异常丢失的过期时间，顺带补回。
     */
    private static final RedisScript<Long> INCREMENT_IN_WINDOW = RedisScript.of(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 or redis.call('TTL', KEYS[1]) < 0 then "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private TrustedProxyPolicy trustedProxyPolicy;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String uri = canonicalPath(request);
        String clientIp = trustedProxyPolicy.resolveClientIp(request);

        for (Subject subject : resolveSubjects(request, uri, clientIp)) {
            if (isExceeded(uri, subject)) {
                log.warn("请求频率超限 - 主体: {}, IP: {}, 端点: {}, 上限: {}/{}s",
                        subject.key(), clientIp, uri, subject.maxRequests(), WINDOW_SECONDS);
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
                return false;
            }
        }
        return true;
    }

    /**
     * 同一请求可能同时受多个主体维度约束（如 OTA 同时按设备与按 IP），任一维度超限即拒绝
     */
    private List<Subject> resolveSubjects(HttpServletRequest request, String uri, String clientIp) {
        List<Subject> subjects = new ArrayList<>(2);
        // 设备列表与设备绑定共用 /api/device 路径，只有写入方向需要限流
        if (isDeviceBindPath(uri) && !"POST".equalsIgnoreCase(request.getMethod())) {
            return subjects;
        }
        if (isOtaEndpoint(uri)) {
            boolean polling = isOtaActivatePath(uri);
            String deviceId = request.getHeader("Device-Id");
            if (deviceId != null && !deviceId.isBlank()) {
                subjects.add(new Subject("device:" + deviceId.trim().toLowerCase(Locale.ROOT),
                        polling ? MAX_OTA_POLL_DEVICE_REQUESTS : MAX_OTA_DEVICE_REQUESTS));
            }
            subjects.add(new Subject("ip:" + clientIp,
                    polling ? MAX_OTA_POLL_IP_REQUESTS : MAX_OTA_IP_REQUESTS));
            return subjects;
        }

        int maxRequests = maxRequestsOf(uri);
        Object loginId = currentLoginId();
        if (loginId != null) {
            subjects.add(new Subject("user:" + loginId, maxRequests));
        } else {
            subjects.add(new Subject("ip:" + clientIp, maxRequests));
        }

        Object account = request.getAttribute(ACCOUNT_ATTRIBUTE);
        if (account instanceof String value && !value.isBlank()) {
            subjects.add(new Subject(accountKey(value), maxRequests));
        }
        return subjects;
    }

    private static boolean isOtaEndpoint(String uri) {
        return uri.startsWith("/api/device/ota");
    }

    private static boolean isOtaActivatePath(String uri) {
        return uri.startsWith("/api/device/ota/activate");
    }

    private static boolean isDeviceBindPath(String uri) {
        return uri.startsWith("/api/device") && !isOtaEndpoint(uri);
    }

    private static int maxRequestsOf(String uri) {
        if (uri.contains("Captcha")) {
            return MAX_CAPTCHA_REQUESTS;
        }
        if (uri.startsWith("/api/device")) {
            return MAX_BIND_REQUESTS;
        }
        if (uri.startsWith("/api/user/checkUser")) {
            return MAX_CHECK_USER_REQUESTS;
        }
        if (uri.startsWith("/api/config/test")) {
            return MAX_CONFIG_TEST_REQUESTS;
        }
        return MAX_AUTH_REQUESTS;
    }

    /**
     * 端点判定与 Redis key 必须用解码归一后的路径：Spring 按解码后的路径选 handler，
     * 直接拿 getRequestURI() 的话 /api/user/lo%67in、/api/user/login;a=b 会各自算成新端点，换个写法就是一份新额度。
     */
    static String canonicalPath(HttpServletRequest request) {
        try {
            return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        } catch (Exception e) {
            return request.getRequestURI();
        }
    }

    /** 账号维度的 key 只存摘要，手机号/邮箱不进 Redis 也不进日志 */
    private static String accountKey(String account) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(account.getBytes(StandardCharsets.UTF_8));
            return "account:" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            return "account:" + Integer.toHexString(account.hashCode());
        }
    }

    /** 登录端点本身是匿名的，取不到登录态属正常情况 */
    private Object currentLoginId() {
        try {
            return StpUtil.getLoginIdDefaultNull();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isExceeded(String uri, Subject subject) {
        String redisKey = RATE_LIMIT_PREFIX + normalizeUri(uri) + ":" + subject.key();
        try {
            Long count = stringRedisTemplate.execute(INCREMENT_IN_WINDOW, List.of(redisKey),
                    String.valueOf(WINDOW_SECONDS));
            return count != null && count > subject.maxRequests();
        } catch (Exception e) {
            // Redis 异常时放行，不影响正常使用
            log.error("限流检查异常，已放行: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 规范化 URI 用于 Redis key（去掉特殊字符，统一格式）
     */
    private String normalizeUri(String uri) {
        return uri.replaceAll("[^a-zA-Z0-9/]", "_");
    }

    /** 限流主体：计数维度 + 该维度的窗口上限 */
    private record Subject(String key, int maxRequests) {
    }

    /**
     * 从账号类端点的 JSON 请求体里取出账号，供限流按账号计数。
     * <p>
     * 请求体只能读一次，读完必须换成可重复读的包装再交给下游，否则 {@code @RequestBody} 拿不到内容。
     * 只处理白名单里的几个端点，且请求体超过 {@link #MAX_BODY_BYTES} 直接拒绝，避免缓冲被当成放大面。
     */
    public static class AccountSubjectFilter extends OncePerRequestFilter {

        /** 请求体里带账号、且需要按账号限流的端点 */
        private static final Set<String> ACCOUNT_BODY_PATHS = Set.of(
                "/api/user/login",
                "/api/user/tel-login",
                "/api/user",
                "/api/user/resetPassword",
                "/api/user/sendEmailCaptcha",
                "/api/user/sendSmsCaptcha");

        /** 账号字段按此顺序取第一个非空值 */
        private static final List<String> ACCOUNT_FIELDS = List.of("username", "tel", "email");

        /** 这几个端点的请求体都是几十字节的小 JSON，留足余量 */
        private static final int MAX_BODY_BYTES = 32 * 1024;

        private static final ObjectMapper BODY_MAPPER = new ObjectMapper();

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            if (!isAccountBodyRequest(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                response.setStatus(413);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":413,\"message\":\"请求体过大\"}");
                return;
            }

            String account = extractAccount(body);
            if (account != null) {
                request.setAttribute(ACCOUNT_ATTRIBUTE, account);
            }
            filterChain.doFilter(new CachedBodyRequest(request, body), response);
        }

        private static boolean isAccountBodyRequest(HttpServletRequest request) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                return false;
            }
            if (!isJsonContentType(request.getContentType())) {
                return false;
            }
            return ACCOUNT_BODY_PATHS.contains(normalizePath(canonicalPath(request)));
        }

        /** Jackson 转换器同时收 application/json 与 application/*+json，两种都要参与账号维度，只认前者会被换个子类型绕开 */
        private static boolean isJsonContentType(String contentType) {
            if (contentType == null) {
                return false;
            }
            int semicolon = contentType.indexOf(';');
            String type = (semicolon < 0 ? contentType : contentType.substring(0, semicolon))
                    .trim().toLowerCase(Locale.ROOT);
            return type.equals("application/json")
                    || (type.startsWith("application/") && type.endsWith("+json"));
        }

        /** 开了尾斜杠匹配，/api/user/login/ 与 /api/user/login 是同一个端点 */
        private static String normalizePath(String uri) {
            if (uri == null) {
                return "";
            }
            return uri.length() > 1 && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        }

        /** 请求体不是合法 JSON 时不参与账号维度，交给下游按正常的参数校验报错 */
        private static String extractAccount(byte[] body) {
            if (body.length == 0) {
                return null;
            }
            try {
                JsonNode root = BODY_MAPPER.readTree(body);
                if (root == null || !root.isObject()) {
                    return null;
                }
                for (String field : ACCOUNT_FIELDS) {
                    JsonNode value = root.get(field);
                    if (value != null && value.isTextual() && !value.asText().isBlank()) {
                        return value.asText().trim().toLowerCase(Locale.ROOT);
                    }
                }
            } catch (Exception e) {
                return null;
            }
            return null;
        }
    }

    /** 请求体已读进内存，下游按需重复读取 */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return source.read(buffer, offset, length);
                }

                @Override
                public int available() {
                    return source.available();
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("缓存的请求体不支持异步读取");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset()));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        private Charset charset() {
            String encoding = getCharacterEncoding();
            if (encoding == null || encoding.isBlank()) {
                return StandardCharsets.UTF_8;
            }
            try {
                return Charset.forName(encoding);
            } catch (Exception e) {
                return StandardCharsets.UTF_8;
            }
        }
    }
}

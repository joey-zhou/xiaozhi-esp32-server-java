package com.xiaozhi.server.config;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.web.TrustedProxyPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 限流拦截器：计数主体取可信 IP / 登录用户 / 设备，账号维度单独计一份且只落摘要。
 * <p>
 * Redis 侧用一张内存计数表还原 INCR 的累加语义，用例断言的是「计数落在哪个 key 上」与「第几次被拒」。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    private static final Object HANDLER = new Object();

    private static final String LOGIN_PATH = "/api/user/login";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private TrustedProxyPolicy trustedProxyPolicy;

    /** Redis key -> 当前窗口内的计数 */
    private final Map<String, Long> counters = new HashMap<>();

    private RateLimitInterceptor rateLimitInterceptor;

    @BeforeEach
    void setUp() {
        rateLimitInterceptor = new RateLimitInterceptor();
        ReflectionTestUtils.setField(rateLimitInterceptor, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(rateLimitInterceptor, "trustedProxyPolicy", trustedProxyPolicy);
    }

    /** 伪造的 IP 只能出现在请求头里，计数必须落在可信来源给出的 IP 上 */
    @Test
    void countsByTrustedIpInsteadOfForwardedHeader() throws Exception {
        givenRedisCounters();
        givenClientIp("10.0.0.7");
        MockHttpServletRequest request = postRequest(LOGIN_PATH);
        request.addHeader("X-Forwarded-For", "9.9.9.9");

        assertThat(rateLimitInterceptor.preHandle(request, new MockHttpServletResponse(), HANDLER)).isTrue();

        assertThat(counters).containsExactly(entry("rate_limit:/api/user/login:ip:10.0.0.7", 1L));
    }

    @Test
    void rejectsWithTooManyRequestsAfterTheAuthLimitIsUsedUp() throws Exception {
        givenRedisCounters();
        givenClientIp("10.0.0.7");
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimitInterceptor.preHandle(postRequest(LOGIN_PATH), new MockHttpServletResponse(), HANDLER))
                .isTrue();
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(rateLimitInterceptor.preHandle(postRequest(LOGIN_PATH), response, HANDLER)).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("请求过于频繁");
    }

    /** 验证码端点比登录更严 */
    @Test
    void appliesStricterLimitOnCaptchaEndpoints() throws Exception {
        givenRedisCounters();
        givenClientIp("10.0.0.7");
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimitInterceptor.preHandle(postRequest("/api/user/sendSmsCaptcha"),
                new MockHttpServletResponse(), HANDLER)).isTrue();
        }

        assertThat(rateLimitInterceptor.preHandle(postRequest("/api/user/sendSmsCaptcha"),
            new MockHttpServletResponse(), HANDLER)).isFalse();
    }

    /** 换 IP 换不掉账号维度的计数，且手机号/邮箱不能进 Redis key */
    @Test
    void countsAccountAcrossChangingIpsAndKeepsOnlyItsDigest() throws Exception {
        givenRedisCounters();
        AtomicInteger host = new AtomicInteger();
        when(trustedProxyPolicy.resolveClientIp(any(HttpServletRequest.class)))
            .thenAnswer(invocation -> "10.0.0." + host.incrementAndGet());

        for (int i = 0; i < 10; i++) {
            assertThat(rateLimitInterceptor.preHandle(loginRequestOf("alice@example.com"),
                new MockHttpServletResponse(), HANDLER)).isTrue();
        }

        assertThat(rateLimitInterceptor.preHandle(loginRequestOf("alice@example.com"),
            new MockHttpServletResponse(), HANDLER)).isFalse();
        assertThat(counters.keySet()).anyMatch(key -> key.startsWith("rate_limit:/api/user/login:account:"));
        assertThat(counters.keySet()).noneMatch(key -> key.contains("alice@example.com"));
    }

    /** 设备绑定是登录后的操作，按登录用户计数，换 IP 也逃不掉 */
    @Test
    void countsDeviceBindByLoginUser() throws Exception {
        givenRedisCounters();
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdDefaultNull).thenReturn(7);

            assertThat(rateLimitInterceptor.preHandle(postRequest("/api/device"),
                new MockHttpServletResponse(), HANDLER)).isTrue();
        }

        assertThat(counters).containsExactly(entry("rate_limit:/api/device:user:7", 1L));
    }

    /** 设备列表与设备绑定共用一个路径，读方向不计数 */
    @Test
    void skipsRateLimitOnDeviceReads() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/device");

        assertThat(rateLimitInterceptor.preHandle(request, new MockHttpServletResponse(), HANDLER)).isTrue();

        assertThat(counters).isEmpty();
        verifyNoInteractions(stringRedisTemplate);
    }

    /** OTA 同时受设备与 IP 两维约束：只按 IP 拦不住单设备刷，只按设备拦不住枚举 MAC */
    @Test
    void countsOtaByBothDeviceAndIp() throws Exception {
        givenRedisCounters();
        givenClientIp("10.0.0.7");
        MockHttpServletRequest request = postRequest("/api/device/ota");
        request.addHeader("Device-Id", "ESP32-01");

        assertThat(rateLimitInterceptor.preHandle(request, new MockHttpServletResponse(), HANDLER)).isTrue();

        assertThat(counters).containsOnly(
            entry("rate_limit:/api/device/ota:device:esp32-01", 1L),
            entry("rate_limit:/api/device/ota:ip:10.0.0.7", 1L));
    }

    /** 编码与矩阵参数写法不能各自算成一个新端点，否则换个写法就是一份新额度 */
    @Test
    void sharesOneCounterAcrossEncodedAndSemicolonPaths() throws Exception {
        givenRedisCounters();
        givenClientIp("10.0.0.7");

        rateLimitInterceptor.preHandle(postRequest("/api/user/lo%67in"), new MockHttpServletResponse(), HANDLER);
        rateLimitInterceptor.preHandle(postRequest("/api/user/login;a=b"), new MockHttpServletResponse(), HANDLER);

        assertThat(counters).containsExactly(entry("rate_limit:/api/user/login:ip:10.0.0.7", 2L));
    }

    @Test
    void allowsRequestWhenRedisIsUnavailable() throws Exception {
        givenClientIp("10.0.0.7");
        when(stringRedisTemplate.<Long>execute(any(), anyList(), anyString()))
            .thenThrow(new IllegalStateException("redis 不可用"));

        assertThat(rateLimitInterceptor.preHandle(postRequest(LOGIN_PATH), new MockHttpServletResponse(), HANDLER))
            .isTrue();
    }

    /** 账号从请求体里取，取完请求体必须还能被下游读到 */
    @Test
    void accountFilterExtractsAccountAndKeepsBodyReadable() throws Exception {
        MockHttpServletRequest request = jsonRequest(LOGIN_PATH, "application/json",
            "{\"username\":\"Alice\",\"password\":\"secret\"}");
        AtomicReference<String> downstreamBody = new AtomicReference<>();

        new RateLimitInterceptor.AccountSubjectFilter().doFilter(request, new MockHttpServletResponse(),
            (req, res) -> downstreamBody.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(request.getAttribute(RateLimitInterceptor.ACCOUNT_ATTRIBUTE)).isEqualTo("alice");
        assertThat(downstreamBody.get()).contains("\"username\":\"Alice\"");
    }

    /** 缓存请求体是为了限流，不能被拿来当放大面 */
    @Test
    void accountFilterRejectsOversizedBody() throws Exception {
        MockHttpServletRequest request = jsonRequest(LOGIN_PATH, "application/json", "");
        request.setContent(new byte[32 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean downstreamCalled = new AtomicBoolean();

        new RateLimitInterceptor.AccountSubjectFilter().doFilter(request, response,
            (req, res) -> downstreamCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(downstreamCalled).isFalse();
    }

    /** 换个 json 子类型同样要参与账号维度 */
    @Test
    void accountFilterCoversJsonSubtypes() throws Exception {
        MockHttpServletRequest request = jsonRequest(LOGIN_PATH, "application/vnd.api+json;charset=UTF-8",
            "{\"tel\":\"13800138000\"}");

        new RateLimitInterceptor.AccountSubjectFilter().doFilter(request, new MockHttpServletResponse(),
            (req, res) -> { });

        assertThat(request.getAttribute(RateLimitInterceptor.ACCOUNT_ATTRIBUTE)).isEqualTo("13800138000");
    }

    @Test
    void accountFilterIgnoresNonJsonBody() throws Exception {
        MockHttpServletRequest request = jsonRequest(LOGIN_PATH, "application/x-www-form-urlencoded",
            "username=alice");

        new RateLimitInterceptor.AccountSubjectFilter().doFilter(request, new MockHttpServletResponse(),
            (req, res) -> { });

        assertThat(request.getAttribute(RateLimitInterceptor.ACCOUNT_ATTRIBUTE)).isNull();
    }

    /** 让 Redis 脚本按 key 累加并返回累加后的计数 */
    private void givenRedisCounters() {
        when(stringRedisTemplate.<Long>execute(any(), anyList(), anyString()))
            .thenAnswer(invocation -> {
                List<?> keys = (List<?>) invocation.getArguments()[1];
                return counters.merge(String.valueOf(keys.get(0)), 1L, Long::sum);
            });
    }

    private void givenClientIp(String ip) {
        when(trustedProxyPolicy.resolveClientIp(any(HttpServletRequest.class))).thenReturn(ip);
    }

    private static MockHttpServletRequest postRequest(String uri) {
        return new MockHttpServletRequest("POST", uri);
    }

    private static MockHttpServletRequest loginRequestOf(String account) {
        MockHttpServletRequest request = postRequest(LOGIN_PATH);
        request.setAttribute(RateLimitInterceptor.ACCOUNT_ATTRIBUTE, account);
        return request;
    }

    private static MockHttpServletRequest jsonRequest(String uri, String contentType, String body) {
        MockHttpServletRequest request = postRequest(uri);
        request.setContentType(contentType);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}

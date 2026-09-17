package com.xiaozhi.server.config;

import com.xiaozhi.file.LocalFileAccessInterceptor;
import com.xiaozhi.server.web.LogInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 限流拦截器的挂载路径：名单漏一个端点，该端点就完全不限速。
 */
@ExtendWith(MockitoExtension.class)
class WebMvcConfigTest {

    @Mock
    private LogInterceptor logInterceptor;

    @Mock
    private RateLimitInterceptor rateLimitInterceptor;

    @Mock
    private LocalFileAccessInterceptor localFileAccessInterceptor;

    private WebMvcConfig webMvcConfig;

    @BeforeEach
    void setUp() {
        webMvcConfig = new WebMvcConfig();
        ReflectionTestUtils.setField(webMvcConfig, "logInterceptor", logInterceptor);
        ReflectionTestUtils.setField(webMvcConfig, "rateLimitInterceptor", rateLimitInterceptor);
        ReflectionTestUtils.setField(webMvcConfig, "localFileAccessInterceptor", localFileAccessInterceptor);
    }

    /** 匿名可达的账号端点、设备绑定与 OTA 都要挂上限流 */
    @Test
    void rateLimitCoversAuthDeviceBindAndOtaEndpoints() {
        assertThat(rateLimitPathPatterns()).contains(
            "/api/user/login",
            "/api/user/tel-login",
            "/api/user/wx-login",
            "/api/user",
            "/api/user/resetPassword",
            "/api/user/sendEmailCaptcha",
            "/api/user/sendSmsCaptcha",
            "/api/device",
            "/api/device/scan-bind",
            "/api/device/ota",
            "/api/device/ota/activate");
    }

    /** 账号维度限流依赖过滤器解析请求体，过滤器覆盖不到的端点只能按 IP 计数 */
    @Test
    void accountSubjectFilterCoversUserEndpoints() {
        FilterRegistrationBean<RateLimitInterceptor.AccountSubjectFilter> registration =
            webMvcConfig.accountSubjectFilter();

        assertThat(registration.getUrlPatterns()).containsExactlyInAnyOrder("/api/user", "/api/user/*");
    }

    private List<String> rateLimitPathPatterns() {
        InterceptorRegistry registry = new InterceptorRegistry();
        webMvcConfig.addInterceptors(registry);
        List<Object> interceptors = ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
        return interceptors.stream()
            .filter(MappedInterceptor.class::isInstance)
            .map(MappedInterceptor.class::cast)
            .filter(mapped -> mapped.getInterceptor() == rateLimitInterceptor)
            .flatMap(mapped -> Arrays.stream(mapped.getIncludePathPatterns()))
            .toList();
    }
}

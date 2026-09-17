package com.xiaozhi.operationlog.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.model.bo.OperationLogBO;
import com.xiaozhi.common.web.TrustedProxyPolicy;
import com.xiaozhi.operationlog.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计日志的来源 IP：由可信代理判定给出，直接采信请求头的话审计表可被投毒。
 */
@ExtendWith(MockitoExtension.class)
class AuditLogAspectClientIpTest {

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private TrustedProxyPolicy trustedProxyPolicy;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private AuditLogAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditLogAspect();
        ReflectionTestUtils.setField(aspect, "operationLogService", operationLogService);
        ReflectionTestUtils.setField(aspect, "trustedProxyPolicy", trustedProxyPolicy);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void aroundRecordsTrustedClientIpInsteadOfForwardedHeader() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/user/login");
        request.addHeader("X-Forwarded-For", "9.9.9.9");
        request.setQueryString("from=web");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(trustedProxyPolicy.resolveClientIp(request)).thenReturn("10.0.0.7");

        OperationLogBO saved = captureLog();

        assertThat(saved.getIp()).isEqualTo("10.0.0.7");
        assertThat(saved.getUrl()).isEqualTo("/api/user/login?from=web");
        assertThat(saved.getMethod()).isEqualTo("POST");
    }

    private OperationLogBO captureLog() throws Throwable {
        Method method = AuditedTarget.class.getDeclaredMethod("login");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new AuditedTarget());

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(false);
            aspect.around(joinPoint, method.getAnnotation(AuditLog.class));
        }

        ArgumentCaptor<OperationLogBO> captor = ArgumentCaptor.forClass(OperationLogBO.class);
        verify(operationLogService).saveAsync(captor.capture());
        return captor.getValue();
    }

    static class AuditedTarget {

        @AuditLog(module = "用户管理", operation = "用户登录")
        public void login() {
        }
    }
}

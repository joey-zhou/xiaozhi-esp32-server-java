package com.xiaozhi.operationlog.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.model.bo.OperationLogBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.UserLoginReq;
import com.xiaozhi.operationlog.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogAspectSensitiveTest {

    private static final String PLAINTEXT_PASSWORD = "hunter2-plaintext";
    private static final String PLAINTEXT_API_KEY = "ak-live-0123456789";
    private static final String PLAINTEXT_API_SECRET = "as-live-9876543210";
    private static final String PLAINTEXT_AK = "AKIAIOSFODNN7EXAMPLE";
    private static final String PLAINTEXT_SK = "wJalrXUtnFEMIK7MDENGbPxRfiCYEXAMPLEKEY";

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private AuditLogAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditLogAspect();
        ReflectionTestUtils.setField(aspect, "operationLogService", operationLogService);
    }

    @Test
    void aroundMasksPasswordInsteadOfLoggingPlaintext() throws Throwable {
        String params = capturedParams("login", UserLoginReq.class, loginReq());

        assertThat(params).doesNotContain(PLAINTEXT_PASSWORD);
        assertThat(params).contains("\"password\":\"" + AuditLogAspect.MASK + "\"");
        assertThat(params).contains("\"username\":\"joey\"");
    }

    @Test
    void aroundMasksEveryConfigCredentialButKeepsAddressAndProvider() throws Throwable {
        String params = capturedParams("createConfig", ConfigCreateReq.class, configReq());

        assertThat(params).doesNotContain(PLAINTEXT_API_KEY, PLAINTEXT_API_SECRET, PLAINTEXT_AK, PLAINTEXT_SK);
        assertThat(params).contains(
                "\"apiKey\":\"" + AuditLogAspect.MASK + "\"",
                "\"apiSecret\":\"" + AuditLogAspect.MASK + "\"",
                "\"ak\":\"" + AuditLogAspect.MASK + "\"",
                "\"sk\":\"" + AuditLogAspect.MASK + "\"");
        assertThat(params).contains("\"apiUrl\":\"https://api.example.com/v1\"");
        assertThat(params).contains("\"provider\":\"openai\"");
    }

    @Test
    void aroundDropsParamsWhenSerializationFails() throws Throwable {
        String params = capturedParams("explode", Object.class, new UnserializableArg());

        // 序列化失败不许回退到未打码的序列化器
        assertThat(params).isNull();
    }

    private String capturedParams(String methodName, Class<?> paramType, Object arg) throws Throwable {
        Method method = AuditedTarget.class.getDeclaredMethod(methodName, paramType);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new AuditedTarget());
        when(joinPoint.getArgs()).thenReturn(new Object[]{arg});

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(false);
            aspect.around(joinPoint, method.getAnnotation(AuditLog.class));
        }

        ArgumentCaptor<OperationLogBO> captor = ArgumentCaptor.forClass(OperationLogBO.class);
        verify(operationLogService).saveAsync(captor.capture());
        return captor.getValue().getParams();
    }

    private static UserLoginReq loginReq() {
        UserLoginReq req = new UserLoginReq();
        req.setUsername("joey");
        req.setPassword(PLAINTEXT_PASSWORD);
        return req;
    }

    private static ConfigCreateReq configReq() {
        ConfigCreateReq req = new ConfigCreateReq();
        req.setConfigName("主力模型");
        req.setConfigType("llm");
        req.setProvider("openai");
        req.setApiUrl("https://api.example.com/v1");
        req.setApiKey(PLAINTEXT_API_KEY);
        req.setApiSecret(PLAINTEXT_API_SECRET);
        req.setAk(PLAINTEXT_AK);
        req.setSk(PLAINTEXT_SK);
        return req;
    }

    static class UnserializableArg {
        public String getBoom() {
            throw new IllegalStateException("序列化必须失败");
        }
    }

    static class AuditedTarget {

        @AuditLog(module = "用户管理", operation = "用户登录")
        public void login(UserLoginReq req) {
        }

        @AuditLog(module = "配置管理", operation = "创建配置")
        public void createConfig(ConfigCreateReq req) {
        }

        @AuditLog(module = "测试", operation = "序列化失败")
        public void explode(Object arg) {
        }
    }
}

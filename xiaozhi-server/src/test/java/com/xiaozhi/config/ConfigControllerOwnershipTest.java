package com.xiaozhi.config;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.req.ConfigTestReq;
import com.xiaozhi.security.ownership.OwnershipAspect;
import com.xiaozhi.security.ownership.OwnershipChecker;
import com.xiaozhi.user.service.UserService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住 /api/config/test 走归属校验：请求体带 configId 时必须交给 config 检查器，
 * 未保存的表单（configId 为空）才跳过。
 */
@ExtendWith(MockitoExtension.class)
class ConfigControllerOwnershipTest {

    private static final int CURRENT_USER_ID = 9;

    @Mock
    private OwnershipChecker ownershipChecker;

    @Mock
    private UserService userService;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Test
    void configTestEndpointChecksConfigOwnership() throws Exception {
        when(ownershipChecker.getResource()).thenReturn("config");
        when(userService.getBO(CURRENT_USER_ID)).thenReturn(normalUser());
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(testEndpoint());
        when(joinPoint.getArgs()).thenReturn(new Object[]{request(7)});

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginId).thenReturn(CURRENT_USER_ID);

            newAspect().checkOwner(joinPoint);

            verify(ownershipChecker).check(7, CURRENT_USER_ID);
        }
    }

    @Test
    void configTestEndpointSkipsCheckForUnsavedForm() throws Exception {
        when(ownershipChecker.getResource()).thenReturn("config");
        when(userService.getBO(CURRENT_USER_ID)).thenReturn(normalUser());
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(testEndpoint());
        when(joinPoint.getArgs()).thenReturn(new Object[]{request(null)});

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginId).thenReturn(CURRENT_USER_ID);

            newAspect().checkOwner(joinPoint);

            verify(ownershipChecker, never()).check(any(), any());
        }
    }

    private OwnershipAspect newAspect() {
        OwnershipAspect aspect = new OwnershipAspect(List.of(ownershipChecker));
        ReflectionTestUtils.setField(aspect, "userService", userService);
        return aspect;
    }

    private static Method testEndpoint() throws NoSuchMethodException {
        return ConfigController.class.getDeclaredMethod("test", ConfigTestReq.class);
    }

    private static ConfigTestReq request(Integer configId) {
        ConfigTestReq req = new ConfigTestReq();
        req.setConfigId(configId);
        req.setConfigType("llm");
        req.setConfigName("测试配置");
        req.setProvider("openai");
        return req;
    }

    private static UserBO normalUser() {
        UserBO user = new UserBO();
        user.setIsAdmin("0");
        return user;
    }
}

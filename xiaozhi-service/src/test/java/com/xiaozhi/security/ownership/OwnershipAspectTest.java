package com.xiaozhi.security.ownership;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.user.service.UserService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnershipAspectTest {

    @Mock
    private OwnershipChecker ownershipChecker;

    @Mock
    private UserService userService;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void checkOwnerShouldReadUserIdFromSaTokenWithoutRequestContext() throws Exception {
        Method method = TestMethods.class.getDeclaredMethod("updateRole", Integer.class);

        when(ownershipChecker.getResource()).thenReturn("role");
        OwnershipAspect aspect = newAspect();
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{123});

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginId).thenReturn("7");

            aspect.checkOwner(joinPoint);

            stpUtil.verify(StpUtil::checkLogin);
            verify(ownershipChecker).check(123, 7);
            verifyNoInteractions(userService);
        }
    }

    @Test
    void checkOwnerShouldRejectInvalidLoginId() throws Exception {
        Method method = TestMethods.class.getDeclaredMethod("updateRole", Integer.class);

        when(ownershipChecker.getResource()).thenReturn("role");
        OwnershipAspect aspect = newAspect();
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginId).thenReturn("abc");

            assertThatThrownBy(() -> aspect.checkOwner(joinPoint))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("无法获取当前登录用户");
        }
    }

    private OwnershipAspect newAspect() {
        OwnershipAspect aspect = new OwnershipAspect(List.of(ownershipChecker));
        ReflectionTestUtils.setField(aspect, "userService", userService);
        return aspect;
    }

    private static class TestMethods {

        @CheckOwner(resource = "role", id = "#roleId", adminBypass = false)
        void updateRole(Integer roleId) {
        }
    }
}

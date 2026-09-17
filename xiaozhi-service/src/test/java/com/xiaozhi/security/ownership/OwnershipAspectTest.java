package com.xiaozhi.security.ownership;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.bo.UserBO;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住归属校验切面在无 Web 请求上下文时也能工作：用户ID 只从 Sa-Token 取，
 * loginId 不是数字时必须直接拒绝而不是放行；
 * id 表达式引用了方法上不存在的参数名时必须报错，不能求值为 null 后静默跳过校验；
 * adminBypass 只对声明允许的接口生效；批量入参必须逐项校验。
 */
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
    void checkOwnerReadsUserIdFromSaTokenWithoutRequestContext() throws Exception {
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
    void checkOwnerRejectsInvalidLoginId() throws Exception {
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

    @Test
    void checkOwnerRejectsExpressionReferencingUnknownParameter() throws Exception {
        Method method = TestMethods.class.getDeclaredMethod("updateRenamedRole", Integer.class);

        when(ownershipChecker.getResource()).thenReturn("role");
        OwnershipAspect aspect = newAspect();
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);

        assertThatThrownBy(() -> aspect.checkOwner(joinPoint))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("#roleId");

        verify(ownershipChecker, never()).check(any(), any());
    }

    @Test
    void adminSkipsCheckerWhenBypassAllowed() throws Exception {
        Method method = TestMethods.class.getDeclaredMethod("readRole", Integer.class);

        when(ownershipChecker.getResource()).thenReturn("role");
        OwnershipAspect aspect = newAspect();
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{123});
        when(userService.getBO(7)).thenReturn(admin());

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginId).thenReturn("7");

            aspect.checkOwner(joinPoint);

            verify(ownershipChecker, never()).check(any(), any());
        }
    }

    /** adminBypass=false 的接口连管理员也要过归属校验，否则等于把这个开关废掉。 */
    @Test
    void adminStillCheckedWhenBypassDisabled() throws Exception {
        Method method = TestMethods.class.getDeclaredMethod("updateRole", Integer.class);

        when(ownershipChecker.getResource()).thenReturn("role");
        OwnershipAspect aspect = newAspect();
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{123});

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginId).thenReturn("7");

            aspect.checkOwner(joinPoint);

            verify(ownershipChecker).check(123, 7);
            verifyNoInteractions(userService);
        }
    }

    /** 批量入参逐项校验，只跳过 null 与空串，漏掉任何一项都是横向越权。 */
    @Test
    void checksEveryElementOfCollectionExpression() throws Exception {
        Method method = TestMethods.class.getDeclaredMethod("batchUpdateRoles", List.class);

        when(ownershipChecker.getResource()).thenReturn("role");
        OwnershipAspect aspect = newAspect();
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{Arrays.asList("1", null, " ", "2")});

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginId).thenReturn("7");

            aspect.checkOwner(joinPoint);

            verify(ownershipChecker).check("1", 7);
            verify(ownershipChecker).check("2", 7);
            verify(ownershipChecker, times(2)).check(any(), any());
        }
    }

    private static UserBO admin() {
        UserBO user = new UserBO();
        user.setUserId(7);
        user.setIsAdmin("1");
        return user;
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

        /** 参数被改名成 id，注解里的 #roleId 已经指不到任何参数。 */
        @CheckOwner(resource = "role", id = "#roleId", adminBypass = false)
        void updateRenamedRole(Integer id) {
        }

        @CheckOwner(resource = "role", id = "#roleId")
        void readRole(Integer roleId) {
        }

        @CheckOwner(resource = "role", id = "#roleIds", adminBypass = false)
        void batchUpdateRoles(List<String> roleIds) {
        }
    }
}

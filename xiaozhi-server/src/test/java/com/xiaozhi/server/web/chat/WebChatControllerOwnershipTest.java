package com.xiaozhi.server.web.chat;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.role.dal.mysql.mapper.RoleMapper;
import com.xiaozhi.security.ownership.OwnershipAspect;
import com.xiaozhi.security.ownership.OwnershipConfig;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 钉住 /api/chat/open 走角色归属校验。
 * 角色的 roleDesc 是用户私有的提示词，这个接口只要不校验 roleId 归属，
 * 任何登录用户拿别人的 roleId 就能开出一个用别人角色的会话并读到提示词内容。
 */
@ExtendWith(MockitoExtension.class)
class WebChatControllerOwnershipTest {

    private static final Integer OWNER_USER_ID = 7;
    private static final Integer OTHER_USER_ID = 8;
    private static final Integer ROLE_ID = 3;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserService userService;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Test
    void openRejectsAnotherUsersRole() throws Exception {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(OWNER_USER_ID));
        when(userService.getBO(OTHER_USER_ID)).thenReturn(normalUser());
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(openEndpoint());
        when(joinPoint.getArgs()).thenReturn(new Object[]{ROLE_ID, null});

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginId).thenReturn(OTHER_USER_ID);

            OwnershipAspect aspect = newAspect();

            assertThatThrownBy(() -> aspect.checkOwner(joinPoint))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("角色不归属当前用户");
        }
    }

    @Test
    void openAcceptsOwnRole() throws Exception {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(role(OWNER_USER_ID));
        when(userService.getBO(OWNER_USER_ID)).thenReturn(normalUser());
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(openEndpoint());
        when(joinPoint.getArgs()).thenReturn(new Object[]{ROLE_ID, "session-1"});

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginId).thenReturn(OWNER_USER_ID);

            OwnershipAspect aspect = newAspect();

            assertThatCode(() -> aspect.checkOwner(joinPoint)).doesNotThrowAnyException();
        }
    }

    private OwnershipAspect newAspect() {
        OwnershipAspect aspect = new OwnershipAspect(List.of(new OwnershipConfig().roleOwnershipChecker(roleMapper)));
        ReflectionTestUtils.setField(aspect, "userService", userService);
        return aspect;
    }

    private static Method openEndpoint() throws NoSuchMethodException {
        return WebChatController.class.getDeclaredMethod("open", Integer.class, String.class);
    }

    private static RoleDO role(Integer userId) {
        RoleDO role = new RoleDO();
        role.setRoleId(ROLE_ID);
        role.setUserId(userId);
        return role;
    }

    private static UserBO normalUser() {
        UserBO user = new UserBO();
        user.setIsAdmin("0");
        return user;
    }
}

package com.xiaozhi.user;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.resp.LoginResp;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.security.service.AuthenticationService;
import com.xiaozhi.user.service.UserService;
import com.xiaozhi.user.service.WxLoginService;
import com.xiaozhi.verifycode.service.VerifyCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 三种登录都在这里记登录信息、签发 token、组装出参；建号走事务代理，不能自调用绕过 */
@ExtendWith(MockitoExtension.class)
class UserAppServiceSignInTest {

    @Mock
    private UserService userService;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private VerifyCodeService verifyCodeService;
    @Mock
    private WxLoginService wxLoginService;
    /** 代表事务代理：建号与改状态必须经它调，自调用会绕过事务 */
    @Mock
    private UserAppService transactionalProxy;

    private UserAppService userAppService;

    @BeforeEach
    void setUp() {
        userAppService = spy(new UserAppService());
        ReflectionTestUtils.setField(userAppService, "userService", userService);
        ReflectionTestUtils.setField(userAppService, "authenticationService", authenticationService);
        ReflectionTestUtils.setField(userAppService, "verifyCodeService", verifyCodeService);
        ReflectionTestUtils.setField(userAppService, "wxLoginService", wxLoginService);
        ReflectionTestUtils.setField(userAppService, "self", transactionalProxy);
    }

    private static UserBO user(int userId) {
        UserBO user = new UserBO();
        user.setUserId(userId);
        user.setPassword("hash");
        return user;
    }

    private void loginRespFor(int userId, boolean newUser) {
        doReturn(LoginResp.builder().userId(userId).isNewUser(newUser).build())
                .when(userAppService).buildLoginResp(eq(userId), eq("tk"), eq(newUser));
    }

    @Test
    void aPasswordSignInRecordsTheLoginAndIssuesAToken() {
        when(userService.getByUsername("alice")).thenReturn(user(3));
        when(authenticationService.isPasswordValid("secret", "hash")).thenReturn(true);
        loginRespFor(3, false);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenValue).thenReturn("tk");

            assertThat(userAppService.signIn("alice", "secret", "10.0.0.7").getUserId()).isEqualTo(3);

            stp.verify(() -> StpUtil.login(eq(3), anyLong()));
        }
        // 只写登录列：整份回写会盖掉同一时刻管理端做的禁用、改密
        verify(userService).recordLogin(3, "10.0.0.7");
        verify(userService, never()).update(any());
    }

    @Test
    void aWrongCodeSignsNobodyIn() {
        assertThatThrownBy(() -> userAppService.signInByTel("13800138000", "000000", "10.0.0.7"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("验证码错误或已过期");
        verify(transactionalProxy, never()).loginByTel(any());
    }

    @Test
    void aTelSignInCreatesTheAccountThroughTheTransactionalProxy() {
        when(verifyCodeService.consumeByAccount("13800138000", "123456")).thenReturn(true);
        when(transactionalProxy.loginByTel("13800138000")).thenReturn(user(4));
        loginRespFor(4, false);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenValue).thenReturn("tk");

            assertThat(userAppService.signInByTel("13800138000", "123456", "10.0.0.7").getUserId()).isEqualTo(4);
        }
    }

    @Test
    void aWechatSignInKeepsTheProfileAndTellsANewUser() {
        when(wxLoginService.getWxLoginInfo("code")).thenReturn(Map.of("openid", "o1", "unionid", "u1"));
        when(transactionalProxy.loginByWechat(eq("o1"), eq("u1"), contains("\"openid\":\"o1\"")))
                .thenReturn(new UserAppService.WechatLogin(user(5), true));
        loginRespFor(5, true);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenValue).thenReturn("tk");

            assertThat(userAppService.signInByWechat("code", "10.0.0.7").getIsNewUser()).isTrue();
        }
    }

    @Test
    void wechatWithoutAnOpenIdFails() {
        when(wxLoginService.getWxLoginInfo("code")).thenReturn(Map.of());

        assertThatThrownBy(() -> userAppService.signInByWechat("code", "10.0.0.7"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nobodyCanDisableTheirOwnAccount() {
        assertThatThrownBy(() -> userAppService.changeState(1, 1, UserBO.STATE_DISABLED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不能禁用当前登录账号");
        verify(transactionalProxy, never()).updateState(any(), any());
    }

    @Test
    void disablingAnAccountRevokesItsTokens() {
        when(transactionalProxy.updateState(2, UserBO.STATE_DISABLED)).thenReturn(new UserResp());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            userAppService.changeState(1, 2, UserBO.STATE_DISABLED);

            stp.verify(() -> StpUtil.logout(2));
        }
    }
}

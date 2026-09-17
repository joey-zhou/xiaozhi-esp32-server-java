package com.xiaozhi.user;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.req.UserPageReq;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.resp.LoginResp;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.common.web.TrustedProxyPolicy;
import com.xiaozhi.support.ControllerTestSupport;
import com.xiaozhi.user.service.UserService;
import com.xiaozhi.user.service.WxLoginService;
import com.xiaozhi.verifycode.service.VerifyCodeService;
import com.xiaozhi.utils.CaptchaUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private UserAppService userAppService;

    @Mock
    private UserService userService;

    @Mock
    private WxLoginService wxLoginService;

    @Mock
    private VerifyCodeService verifyCodeService;

    @Mock
    private CaptchaUtils captchaUtils;

    @Mock
    private TrustedProxyPolicy trustedProxyPolicy;

    private UserController userController;

    @BeforeEach
    void setUp() {
        userController = new UserController();
        ReflectionTestUtils.setField(userController, "userAppService", userAppService);
        ReflectionTestUtils.setField(userController, "userService", userService);
        ReflectionTestUtils.setField(userController, "wxLoginService", wxLoginService);
        ReflectionTestUtils.setField(userController, "verifyCodeService", verifyCodeService);
        ReflectionTestUtils.setField(userController, "captchaUtils", captchaUtils);
        ReflectionTestUtils.setField(userController, "trustedProxyPolicy", trustedProxyPolicy);
        mockMvc = buildMockMvc(userController);
    }

    @Test
    void checkTokenReturnsUnauthorizedWhenNoUserInContext() throws Exception {
        try (var ignored = mockNoLoginUser()) {
            mockMvc.perform(get("/api/user/check-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("Token无效或已过期"));
        }
    }

    @Test
    void queryUsersReturnsPagedUsers() throws Exception {
        UserResp userResp = new UserResp();
        userResp.setUserId(1);
        userResp.setUsername("alice");
        PageResult<UserResp> pageResp = new PageResult<>(List.of(userResp), 1L, 1, 10);
        when(userAppService.page(any(UserPageReq.class))).thenReturn(pageResp);

        mockMvc.perform(get("/api/user").param("pageNo", "1").param("pageSize", "10").param("name", "ali"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
            .andExpect(jsonPath("$.data.list[0].userId").value(1));

        ArgumentCaptor<UserPageReq> captor = ArgumentCaptor.forClass(UserPageReq.class);
        verify(userAppService).page(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("ali");
    }

    @Test
    void sendEmailCaptchaRejectsUnknownEmailOnForgetFlow() throws Exception {
        when(userService.getByEmail("nobody@example.com")).thenReturn(null);

        mockMvc.perform(post("/api/user/sendEmailCaptcha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"nobody@example.com","type":"forget"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("该邮箱未注册"));
    }

    /** 提示不区分字段，否则这个匿名端点就是逐字段确认注册状态的预言机 */
    @Test
    void checkUserReturnsConflictWhenTelExists() throws Exception {
        when(userService.getByTel("13800138000")).thenReturn(new UserBO());

        mockMvc.perform(get("/api/user/checkUser").param("tel", "13800138000"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ResultStatus.CONFLICT))
            .andExpect(jsonPath("$.message").value("该手机号、邮箱或用户名已被注册"));
    }

    /** loginIp 是安全审计字段，取值必须来自可信代理判定，不能由请求头决定 */
    @Test
    void loginRecordsTrustedClientIpInsteadOfForwardedHeader() throws Exception {
        UserBO user = new UserBO();
        user.setUserId(3);
        when(userAppService.login("alice", "secret")).thenReturn(user);
        when(userAppService.getTokenExpireSeconds()).thenReturn(60);
        when(trustedProxyPolicy.resolveClientIp(any(HttpServletRequest.class))).thenReturn("10.0.0.7");
        when(userAppService.buildLoginResp(eq(3), any(), eq(false)))
            .thenReturn(LoginResp.builder().userId(3).build());

        try (var ignored = mockLoginUser(3)) {
            mockMvc.perform(post("/api/user/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "9.9.9.9")
                    .content("""
                        {"username":"alice","password":"secret"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(3));
        }

        verify(userAppService).recordLoginInfo(user, "10.0.0.7");
    }

    @Test
    void updateStateRefusesToDisableTheCallersOwnAccount() throws Exception {
        try (var ignored = mockLoginUser(1)) {
            mockMvc.perform(put("/api/user/1/state").param("state", UserBO.STATE_DISABLED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能禁用当前登录账号"));
        }
        verifyNoInteractions(userAppService);
    }

    @Test
    void updateStateRevokesIssuedTokensOfTheDisabledAccount() throws Exception {
        UserResp updated = new UserResp();
        updated.setUserId(2);
        updated.setState(UserBO.STATE_DISABLED);
        when(userAppService.updateState(2, UserBO.STATE_DISABLED)).thenReturn(updated);

        try (var stpUtil = mockLoginUser(1)) {
            mockMvc.perform(put("/api/user/2/state").param("state", UserBO.STATE_DISABLED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value(UserBO.STATE_DISABLED));

            stpUtil.verify(() -> StpUtil.logout(2));
        }
    }

    /** 前端登出只清本地 token 的话，服务端签发的 Token 仍然有效，必须调到这个接口。 */
    @Test
    void logoutRevokesCurrentToken() throws Exception {
        try (var stpUtil = mockLoginUser(1)) {
            mockMvc.perform(post("/api/user/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS));

            stpUtil.verify(StpUtil::logout);
        }
        verifyNoInteractions(userAppService);
    }
}

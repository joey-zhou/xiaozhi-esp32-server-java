package com.xiaozhi.controller;

import com.xiaozhi.user.UserController;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.common.model.req.UserPageReq;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.model.resp.UserResp;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.user.UserAppService;
import com.xiaozhi.user.service.UserService;
import com.xiaozhi.user.service.WxLoginService;
import com.xiaozhi.userauth.service.UserAuthService;
import com.xiaozhi.utils.CaptchaUtils;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private AuthenticationService authenticationService;

    @Mock
    private WxLoginService wxLoginService;

    @Mock
    private UserAuthService userAuthService;

    @Mock
    private CaptchaUtils captchaUtils;

    private UserController userController;

    @BeforeEach
    void setUp() {
        userController = new UserController();
        ReflectionTestUtils.setField(userController, "userAppService", userAppService);
        ReflectionTestUtils.setField(userController, "userService", userService);
        ReflectionTestUtils.setField(userController, "authenticationService", authenticationService);
        ReflectionTestUtils.setField(userController, "wxLoginService", wxLoginService);
        ReflectionTestUtils.setField(userController, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(userController, "captchaUtils", captchaUtils);
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
        PageResp<UserResp> pageResp = new PageResp<>(List.of(userResp), 1L, 1, 10);
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

    @Test
    void checkUserReturnsConflictWhenTelExists() throws Exception {
        when(userService.getByTel("13800138000")).thenReturn(new UserBO());

        mockMvc.perform(get("/api/user/checkUser").param("tel", "13800138000"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ResultStatus.CONFLICT))
            .andExpect(jsonPath("$.message").value("手机已注册"));
    }
}

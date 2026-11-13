package com.xiaozhi.communication.server.websocket;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.communication.auth.DeviceAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * 握手是设备接入的唯一关卡：token 与 device-id 必须成对校验，
 * 放过别的设备的 token 或别的用户的登录态，等于任何人都能接进别人的会话。
 */
class DeviceAuthHandshakeInterceptorTest {

    private static final String DEVICE_ID = "aa:bb:cc:dd:ee:ff";

    private DeviceAuthService authService;
    private DeviceAuthHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        authService = new DeviceAuthService();
        ReflectionTestUtils.setField(authService, "secret", "test-secret");
        ReflectionTestUtils.setField(authService, "expireSeconds", 3600L);
        ReflectionTestUtils.setField(authService, "allowedDevicesConfig", "ff:ff:ff:ff:ff:fe");
        ReflectionTestUtils.invokeMethod(authService, "init");

        interceptor = new DeviceAuthHandshakeInterceptor();
        ReflectionTestUtils.setField(interceptor, "deviceAuthService", authService);
    }

    private boolean handshake(MockHttpServletRequest request, MockHttpServletResponse response) {
        return interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), null, new HashMap<>());
    }

    @Test
    void allowsAnythingWhenSecretNotConfigured() {
        ReflectionTestUtils.setField(authService, "secret", "");

        assertThat(handshake(new MockHttpServletRequest(), new MockHttpServletResponse())).isTrue();
    }

    @Test
    void allowsDeviceWithValidTokenInHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("device-id", DEVICE_ID);
        request.addHeader("Authorization", "Bearer " + authService.generateDeviceToken(DEVICE_ID));

        assertThat(handshake(request, new MockHttpServletResponse())).isTrue();
    }

    @Test
    void rejectsDeviceWithoutToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("device-id", DEVICE_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(handshake(request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void rejectsDeviceWithTokenOfOtherDevice() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("device-id", DEVICE_ID);
        request.addHeader("Authorization", "Bearer " + authService.generateDeviceToken("11:22:33:44:55:66"));

        assertThat(handshake(request, new MockHttpServletResponse())).isFalse();
    }

    @Test
    void rejectsWhenDeviceIdMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + authService.generateDeviceToken(DEVICE_ID));

        assertThat(handshake(request, new MockHttpServletResponse())).isFalse();
    }

    @Test
    void allowsWhitelistedDeviceWithoutToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("device-id", "FF:FF:FF:FF:FF:FE");

        assertThat(handshake(request, new MockHttpServletResponse())).isTrue();
    }

    @Test
    void allowsDeviceTokenViaQueryParams() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("device-id=" + DEVICE_ID.replace(":", "%3A")
                + "&token=" + authService.generateDeviceToken(DEVICE_ID));

        assertThat(handshake(request, new MockHttpServletResponse())).isTrue();
    }

    @Test
    void allowsWebClientWithValidLoginToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("device-id=user_chat_7&token=login-token");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getLoginIdByToken("login-token")).thenReturn("7");

            assertThat(handshake(request, new MockHttpServletResponse())).isTrue();
        }
    }

    @Test
    void rejectsWebClientImpersonatingOtherUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("device-id=user_chat_8&token=login-token");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getLoginIdByToken("login-token")).thenReturn("7");

            assertThat(handshake(request, new MockHttpServletResponse())).isFalse();
        }
    }

    /**
     * 设备鉴权密钥留空（docker 默认形态）只该放开 MAC 设备，网页聊天身份仍要认登录态，
     * 否则任何人不带凭据就能以管理员的网页聊天身份接入
     */
    @Test
    void webChatStillRequiresLoginWhenDeviceAuthDisabled() {
        ReflectionTestUtils.setField(authService, "secret", "");

        MockHttpServletRequest anonymous = new MockHttpServletRequest();
        anonymous.setQueryString("device-id=user_chat_1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(handshake(anonymous, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());

        MockHttpServletRequest loggedIn = new MockHttpServletRequest();
        loggedIn.setQueryString("device-id=user_chat_1&token=login-token");
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getLoginIdByToken("login-token")).thenReturn("1");

            assertThat(handshake(loggedIn, new MockHttpServletResponse())).isTrue();
        }
    }

    /** 库的排序规则不区分大小写，大小写变形的前缀不能绕开登录态校验 */
    @Test
    void webChatPrefixCheckIgnoresCase() {
        ReflectionTestUtils.setField(authService, "secret", "");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("device-id=User_Chat_1");

        assertThat(handshake(request, new MockHttpServletResponse())).isFalse();
    }

    /** 自己的身份换个大小写也不收：同一台设备会以两个会话号并存，重连时清不掉上一条 */
    @Test
    void rejectsCaseVariantOfOwnWebChatIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("device-id=User_Chat_7&token=login-token");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getLoginIdByToken("login-token")).thenReturn("7");

            assertThat(handshake(request, new MockHttpServletResponse())).isFalse();
        }
    }

    /** 鉴权关闭时 MAC 设备照旧放行，改动不能波及真实设备 */
    @Test
    void macDeviceStillPassesWhenDeviceAuthDisabled() {
        ReflectionTestUtils.setField(authService, "secret", "");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("device-id", DEVICE_ID);

        assertThat(handshake(request, new MockHttpServletResponse())).isTrue();
    }

    @Test
    void rejectsWebClientWithInvalidLoginToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("device-id=user_chat_7&token=bad-token");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getLoginIdByToken("bad-token")).thenReturn(null);

            assertThat(handshake(request, new MockHttpServletResponse())).isFalse();
        }
    }
}

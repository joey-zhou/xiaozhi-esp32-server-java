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

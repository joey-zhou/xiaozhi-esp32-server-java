package com.xiaozhi.communication.protocol;

import com.xiaozhi.communication.auth.DeviceAuthService;
import com.xiaozhi.communication.server.websocket.DeviceAuthHandshakeInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住 OTA 下发的凭据与 WebSocket 接入之间的闭环：本项目的 OTA 不做算法协商，
 * 设备拿到什么就用什么，所以要保证的是「OTA 用什么算法签发，接入端就用什么算法校验」。
 * OTA 响应里 websocket.token 由 DeviceAuthService#generateDeviceToken 签发，
 * 握手时必须能过 DeviceAuthHandshakeInterceptor；换设备或过期后必须被 401 挡下。
 *
 * <p>用例不实调 OTA 接口（它在 xiaozhi-server 模块），而是在这里复刻 OTA 的签发算法，
 * 两端一旦有一边改了算法、密钥来源或大小写规则，用例就会红。
 */
class OtaCredentialHandshakeTest {

    private static final String DEVICE_ID = "94:a9:90:2b:dd:18";
    private static final String OTHER_DEVICE_ID = "aa:bb:cc:dd:ee:ff";
    private static final String AUTH_SECRET = "ota-device-auth-secret";
    private static final long EXPIRE_SECONDS = 3600L;

    private DeviceAuthService deviceAuthService;
    private DeviceAuthHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        deviceAuthService = new DeviceAuthService();
        ReflectionTestUtils.setField(deviceAuthService, "secret", AUTH_SECRET);
        ReflectionTestUtils.setField(deviceAuthService, "expireSeconds", EXPIRE_SECONDS);
        ReflectionTestUtils.setField(deviceAuthService, "allowedDevicesConfig", "");
        ReflectionTestUtils.invokeMethod(deviceAuthService, "init");

        interceptor = new DeviceAuthHandshakeInterceptor();
        ReflectionTestUtils.setField(interceptor, "deviceAuthService", deviceAuthService);
    }

    @Test
    void otaIssuedDeviceTokenPassesWebSocketHandshake() {
        // OTA 响应里 websocket.token 就是这么来的
        String otaToken = deviceAuthService.generateDeviceToken(DEVICE_ID);
        assertThat(otaToken).isNotEmpty();

        MockHttpServletResponse accepted = new MockHttpServletResponse();
        assertThat(handshake(request(DEVICE_ID, otaToken), accepted)).isTrue();
        assertThat(accepted.getStatus()).isEqualTo(HttpStatus.OK.value());

        // 同一份 token 换一台设备用不通过，token 与 deviceId 是绑定的
        MockHttpServletResponse stolen = new MockHttpServletResponse();
        assertThat(handshake(request(OTHER_DEVICE_ID, otaToken), stolen)).isFalse();
        assertThat(stolen.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());

        // 签名合法但时间戳早于有效期的 token 也要被挡下（设备长期不重新 OTA 的情况）
        long expiredTs = System.currentTimeMillis() / 1000 - EXPIRE_SECONDS - 60;
        String expiredToken = deviceTokenAt(DEVICE_ID, expiredTs);
        MockHttpServletResponse expired = new MockHttpServletResponse();
        assertThat(handshake(request(DEVICE_ID, expiredToken), expired)).isFalse();
        assertThat(expired.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }


    private boolean handshake(MockHttpServletRequest request, MockHttpServletResponse response) {
        return interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), null, new HashMap<>());
    }

    /** 设备侧握手请求：device-id 走请求头，token 走 Authorization，与固件一致 */
    private static MockHttpServletRequest request(String deviceId, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("device-id", deviceId);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    /**
     * 按 DeviceAuthService 的签发格式复刻一个指定时间戳的设备 token：
     * {@code base64url(hmacSha256(deviceId|ts)).ts}，deviceId 取小写。
     */
    private static String deviceTokenAt(String deviceId, long ts) {
        String content = deviceId.trim().toLowerCase(Locale.ROOT) + "|" + ts;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(AUTH_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
            return signature + "." + ts;
        } catch (Exception e) {
            throw new IllegalStateException("构造设备token失败", e);
        }
    }
}

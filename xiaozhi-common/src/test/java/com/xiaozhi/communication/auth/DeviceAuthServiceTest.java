package com.xiaozhi.communication.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住两种无状态 token 的签发校验闭环：设备 token 为 {@code base64url(hmac(deviceId|ts)).ts}，
 * deviceId 大小写不敏感、未配 secret 时整体停用；视觉 token 为
 * 视觉 token 的载荷字段逐个转义后再用 '|' 拼接，不依赖 secret 开关且畸形载荷一律返回 null。
 */
class DeviceAuthServiceTest {

    private static final String DEVICE_ID = "aa:bb:cc:dd:ee:ff";

    private DeviceAuthService service(String secret) {
        DeviceAuthService service = new DeviceAuthService();
        ReflectionTestUtils.setField(service, "secret", secret);
        ReflectionTestUtils.setField(service, "expireSeconds", 3600L);
        ReflectionTestUtils.setField(service, "allowedDevicesConfig", "");
        ReflectionTestUtils.invokeMethod(service, "init");
        return service;
    }

    @Test
    void deviceTokenRoundTrip() {
        DeviceAuthService service = service("test-secret");
        String token = service.generateDeviceToken(DEVICE_ID);

        assertThat(token).isNotBlank().doesNotContain(" ");
        assertThat(service.verifyDeviceToken(token, DEVICE_ID)).isTrue();
        // deviceId 大小写不敏感
        assertThat(service.verifyDeviceToken(token, "AA:BB:CC:DD:EE:FF")).isTrue();
    }

    @Test
    void deviceTokenRejectsOtherDeviceAndTampering() {
        DeviceAuthService service = service("test-secret");
        String token = service.generateDeviceToken(DEVICE_ID);

        assertThat(service.verifyDeviceToken(token, "11:22:33:44:55:66")).isFalse();
        assertThat(service.verifyDeviceToken("x" + token, DEVICE_ID)).isFalse();
        assertThat(service.verifyDeviceToken("not-a-token", DEVICE_ID)).isFalse();
        assertThat(service.verifyDeviceToken("", DEVICE_ID)).isFalse();
    }

    @Test
    void deviceTokenRejectsWhenExpired() {
        DeviceAuthService service = service("test-secret");
        String token = service.generateDeviceToken(DEVICE_ID);
        ReflectionTestUtils.setField(service, "expireSeconds", -1L);

        assertThat(service.verifyDeviceToken(token, DEVICE_ID)).isFalse();
    }

    @Test
    void deviceTokenRejectsDifferentSecret() {
        String token = service("secret-a").generateDeviceToken(DEVICE_ID);

        assertThat(service("secret-b").verifyDeviceToken(token, DEVICE_ID)).isFalse();
    }

    @Test
    void blankSecretDisablesAuthAndKeepsLegacyEmptyToken() {
        DeviceAuthService service = service("");

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.generateDeviceToken(DEVICE_ID)).isEmpty();
        assertThat(service.verifyDeviceToken("any.123", DEVICE_ID)).isFalse();
    }

    @Test
    void configuredSecretEnablesAuth() {
        assertThat(service("test-secret").isEnabled()).isTrue();
    }

    @Test
    void allowedDevicesMatchesCaseInsensitive() {
        DeviceAuthService service = new DeviceAuthService();
        ReflectionTestUtils.setField(service, "secret", "s");
        ReflectionTestUtils.setField(service, "expireSeconds", 3600L);
        ReflectionTestUtils.setField(service, "allowedDevicesConfig", "AA:BB:CC:DD:EE:FF, 11:22:33:44:55:66");
        ReflectionTestUtils.invokeMethod(service, "init");

        assertThat(service.isAllowedDevice(DEVICE_ID)).isTrue();
        assertThat(service.isAllowedDevice("11:22:33:44:55:66")).isTrue();
        assertThat(service.isAllowedDevice("99:99:99:99:99:99")).isFalse();
        assertThat(service.isAllowedDevice(null)).isFalse();
    }

    @Test
    void visionTokenRoundTrip() {
        DeviceAuthService service = service("");
        String token = service.generateVisionToken("session-1", DEVICE_ID);

        DeviceAuthService.VisionToken payload = service.verifyVisionToken(token);
        assertThat(payload).isNotNull();
        assertThat(payload.sessionId()).isEqualTo("session-1");
        assertThat(payload.deviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    void visionTokenToleratesNullDeviceId() {
        DeviceAuthService service = service("");

        DeviceAuthService.VisionToken payload = service.verifyVisionToken(service.generateVisionToken("s", null));
        assertThat(payload).isNotNull();
        assertThat(payload.deviceId()).isEmpty();
    }

    @Test
    void visionTokenRejectsTamperingAndExpiry() {
        DeviceAuthService service = service("");
        String token = service.generateVisionToken("session-1", DEVICE_ID);

        assertThat(service.verifyVisionToken(token + "x")).isNull();
        assertThat(service.verifyVisionToken("session-1")).isNull();
        assertThat(service.verifyVisionToken(null)).isNull();

        ReflectionTestUtils.setField(service, "expireSeconds", -10L);
        assertThat(service.verifyVisionToken(service.generateVisionToken("session-1", DEVICE_ID))).isNull();
    }

    @Test
    void visionTokenRejectsUndecodablePayloadSegment() {
        DeviceAuthService service = service("");

        // 载荷段不是合法 base64url，解码抛 IllegalArgumentException 时按校验失败处理，不能外抛
        assertThat(service.verifyVisionToken("!!!.whatever")).isNull();
    }

    // 载荷用 '|' 分段，字段本身含 '|' 时必须靠转义还原，不能被切错段
    @Test
    void visionTokenKeepsFieldsContainingSeparator() {
        DeviceAuthService service = service("");

        DeviceAuthService.VisionToken parsed =
                service.verifyVisionToken(service.generateVisionToken("session|1", DEVICE_ID));

        assertThat(parsed).isNotNull();
        assertThat(parsed.sessionId()).isEqualTo("session|1");
        assertThat(parsed.deviceId()).isEqualTo(DEVICE_ID);
    }

    // 段数不足三段的载荷一律判失败
    @Test
    void visionTokenRejectsPayloadWithoutExactlyThreeSegments() {
        DeviceAuthService service = service("");
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("session|device".getBytes(StandardCharsets.UTF_8));

        assertThat(service.verifyVisionToken(payload + ".anything")).isNull();
    }
}

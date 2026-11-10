package com.xiaozhi.common.web;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.utils.DateUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 本地文件地址的时效签名。这套签名是录音、固件等文件在本地存储形态下的唯一访问控制，
 * 路径由日期、设备 MAC、角色 ID 与原始文件名拼成、基本可枚举，签名失效等于这些文件匿名可取。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalFileUrlPolicyTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RuntimePathConfig runtimePathConfig;

    @Mock
    private ServerAddressProvider serverAddressProvider;

    private LocalFileUrlPolicy policy;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString())).thenReturn(true);
        when(runtimePathConfig.getAudioDir()).thenReturn("audio");

        policy = new LocalFileUrlPolicy();
        ReflectionTestUtils.setField(policy, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(policy, "runtimePathConfig", runtimePathConfig);
        ReflectionTestUtils.setField(policy, "serverAddressProvider", serverAddressProvider);
        ReflectionTestUtils.setField(policy, "uploadPath", "uploads");
        ReflectionTestUtils.setField(policy, "urlTtlHours", 6L);
        ReflectionTestUtils.invokeMethod(policy, "initPrefixes");
    }

    @AfterEach
    void tearDown() {
        DateUtils.reset();
    }

    @Test
    void signedUrlForProtectedPathVerifies() {
        String signed = policy.sign("uploads/firmware/app.bin");

        assertThat(signed).contains("exp=").contains("sig=");
        assertThat(verifySigned(signed)).isTrue();
    }

    @Test
    void expiredSignatureIsRejected() {
        String signed = policy.sign("audio/2026/09/22/a.wav");
        assertThat(verifySigned(signed)).isTrue();

        // 往后拨过有效期，同一个地址必须失效
        DateUtils.use(Clock.fixed(DateUtils.instant().plus(Duration.ofHours(7)), ZoneOffset.UTC));
        assertThat(verifySigned(signed)).isFalse();
    }

    @Test
    void tamperedSignatureOrExpiryIsRejected() {
        String signed = policy.sign("uploads/firmware/app.bin");
        String path = signed.substring(0, signed.indexOf('?'));
        long expireAt = Long.parseLong(paramOf(signed, "exp"));
        String signature = paramOf(signed, "sig");

        assertThat(policy.verify(path, String.valueOf(expireAt), signature + "x")).isFalse();
        // 把过期时间往后改而不重签，签名对不上
        assertThat(policy.verify(path, String.valueOf(expireAt + 3600), signature)).isFalse();
        assertThat(policy.verify(path, "not-a-number", signature)).isFalse();
        assertThat(policy.verify(path, null, signature)).isFalse();
        assertThat(policy.verify(path, String.valueOf(expireAt), null)).isFalse();
    }

    @Test
    void stripSignatureRoundTripsBackToThePath() {
        String path = "uploads/firmware/app.bin";

        assertThat(policy.stripSignature(policy.sign(path))).isEqualTo(path);
        // 反复签名不会把参数越堆越多
        assertThat(policy.stripSignature(policy.sign(policy.sign(path)))).isEqualTo(path);
    }

    @Test
    void unprotectedPathsPassWithoutSignature() {
        assertThat(policy.isProtected("models/sense-voice/model.onnx")).isFalse();
        assertThat(policy.sign("models/sense-voice/model.onnx")).isEqualTo("models/sense-voice/model.onnx");
        assertThat(policy.verify("models/sense-voice/model.onnx", null, null)).isTrue();

        // 对象存储的键同样以 uploads/ 开头，但完整 URL 由对象存储自己签名，不能被本策略接管
        assertThat(policy.isProtected("https://cdn.example.com/uploads/a.bin")).isFalse();
    }

    @Test
    void protectionCheckAcceptsBothLeadingSlashForms() {
        assertThat(policy.isProtected("uploads/a.bin")).isTrue();
        assertThat(policy.isProtected("/uploads/a.bin")).isTrue();
        assertThat(policy.isProtected("audio/a.wav")).isTrue();
        assertThat(policy.isProtected("/audio/a.wav")).isTrue();
    }

    private boolean verifySigned(String signed) {
        return policy.verify(signed.substring(0, signed.indexOf('?')), paramOf(signed, "exp"), paramOf(signed, "sig"));
    }

    private static String paramOf(String url, String name) {
        for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return kv[1];
            }
        }
        throw new IllegalArgumentException("地址里没有参数 " + name + ": " + url);
    }
}

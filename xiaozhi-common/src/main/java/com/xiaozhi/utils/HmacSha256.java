package com.xiaozhi.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 本系统自己签发、自己校验的令牌共用的 HMAC-SHA256 原语。
 * <p>
 * 编码方式跟着令牌格式走：设备接入、文件地址放在 URL 里，用 URL 安全、无填充；
 * 要交给按标准 Base64 解码的一方时用标准编码。厂商请求签名按厂商规范写在各自 provider 里，不走这里。
 */
public final class HmacSha256 {

    private static final String ALGORITHM = "HmacSHA256";

    public enum Encoding {
        /** 标准 Base64，带填充 */
        STANDARD,
        /** URL 安全字符集，无填充 */
        URL_SAFE
    }

    private HmacSha256() {
    }

    /**
     * @throws IllegalStateException 密钥为空或 JDK 不支持该算法
     */
    public static String sign(String key, String content, Encoding encoding) {
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("HMAC 密钥为空");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = encoding == Encoding.STANDARD
                ? Base64.getEncoder()
                : Base64.getUrlEncoder().withoutPadding();
            return encoder.encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 签名失败", e);
        }
    }

    /** 定长比较，不因为前缀相同就提前返回；签名缺失算不通过 */
    public static boolean verify(String key, String content, String signature, Encoding encoding) {
        if (signature == null) {
            return false;
        }
        byte[] expected = sign(key, content, encoding).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
    }
}

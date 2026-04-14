package com.xiaozhi.utils;

import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import java.util.regex.Pattern;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;

public class CommonUtils {

    private static final Pattern MAC_PATTERN =
            Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");

    /**
     * 判断 MAC 地址是否合法（格式正确且为单播地址）
     */
    public static boolean isMacAddressValid(String mac) {
        if (!MAC_PATTERN.matcher(mac).matches()) {
            return false;
        }
        int firstByte = Integer.parseInt(mac.toLowerCase().split("[:-]")[0], 16);
        return (firstByte & 1) == 0;
    }


    public static Integer CaptchaCode() {
        StringBuilder str = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            str.append(random.nextInt(10));
        }
        return Integer.valueOf(str.toString());
    }

    public static String left(String str, int index) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        String name = StringUtils.left(str, index);
        return StringUtils.rightPad(name, StringUtils.length(str), "*");
    }

    public static String right(String str, int end) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return StringUtils.leftPad(StringUtils.right(str, end), StringUtils.length(str), "*");
    }

    public static String around(String str, int index, int end) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return StringUtils.left(str, index).concat(StringUtils
                .removeStart(StringUtils.leftPad(StringUtils.right(str, end), StringUtils.length(str), "*"), "***"));
    }

    /**
     * 使用 HMAC-SHA256 对内容签名，返回 Base64 编码结果，失败时返回 null
     */
    public static String hmacSha256(String content, String secretKey) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 对给定的字符串进行MD5加密
     * @param str
     * @return
     */
    public static String md5(String str) {
        StringBuilder result = new StringBuilder();
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] bytes = messageDigest.digest(str.getBytes("UTF-8"));
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1)
                    result.append("0");
                result.append(hex);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such algorithm MD5");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported!");
        }
        return result.toString();
    }
}

package com.xiaozhi.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

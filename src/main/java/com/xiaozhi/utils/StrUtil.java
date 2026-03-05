package com.xiaozhi.utils;

public class StrUtil {
    public static final String EMPTY = "";
    public static boolean isNotEmpty(CharSequence str) {
        return !isEmpty(str);
    }
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

}

package com.xiaozhi.communication;

/**
 * 网页聊天的会话身份：每个用户一台虚拟设备，设备号是 {@code user_chat_<userId>}。
 * <p>
 * 前端 FloatingChat 按同一格式拼设备号；迁移 V69 也按它给老用户补建过，已执行的迁移不能改，前缀不要动。
 */
public final class WebChatDevice {

    public static final String DEVICE_NAME = "网页聊天";

    public static final String DEVICE_TYPE = "web";

    private static final String PREFIX = "user_chat_";

    private WebChatDevice() {
    }

    public static String deviceIdOf(Integer userId) {
        return PREFIX + userId;
    }

    /** 不区分大小写：库的排序规则不区分，User_Chat_1 会查到 user_chat_1 那台设备，区分大小写的判断会被变形写法绕开 */
    public static boolean isWebChat(String deviceId) {
        return deviceId != null && deviceId.length() > PREFIX.length()
            && deviceId.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }

    /** 设备号后缀的用户 ID；不是网页聊天身份或后缀不是整数时为 null */
    public static Integer userIdOf(String deviceId) {
        if (!isWebChat(deviceId)) {
            return null;
        }
        try {
            return Integer.valueOf(deviceId.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

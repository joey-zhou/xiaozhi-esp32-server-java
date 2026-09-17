package com.xiaozhi.device.support;

/** DEVICE 缓存的 key 拼装规则：deviceId 里的冒号换成连字符，读写两侧共用。 */
public final class DeviceCacheKeys {

    private DeviceCacheKeys() {}

    public static String of(String deviceId) {
        return deviceId.replace(":", "-");
    }
}

package com.xiaozhi.role.support;

/** ROLE 缓存的 key 拼装规则，读写两侧共用。 */
public final class RoleCacheKeys {

    private RoleCacheKeys() {}

    public static String of(Integer roleId) {
        return String.valueOf(roleId);
    }
}

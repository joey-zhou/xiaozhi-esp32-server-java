package com.xiaozhi.role.support;

/** ROLE 缓存的 key 拼装规则，读写两侧共用。 */
public final class RoleCacheKeys {

    private RoleCacheKeys() {}

    public static String of(Integer roleId) {
        return String.valueOf(roleId);
    }

    /** 缓存未命中时回源查库的分布式锁 */
    public static String lockOf(Integer roleId) {
        return "role:" + of(roleId);
    }
}

package com.xiaozhi.user.support;

/** USER 缓存的 key 拼装规则，读写两侧共用。 */
public final class UserCacheKeys {

    private UserCacheKeys() {}

    public static String of(Integer userId) {
        return "bo:" + userId;
    }
}

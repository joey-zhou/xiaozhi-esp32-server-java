package com.xiaozhi.permission.support;

/** PERMISSION 缓存的 key 拼装规则，读写两侧共用。 */
public final class PermissionCacheKeys {

    /** 全部启用权限的 key，管理员用它 */
    public static final String ALL_KEYS = "all:keys";

    private PermissionCacheKeys() {}

    public static String authRoleList(Integer authRoleId) {
        return "authRole:list:" + authRoleId;
    }

    public static String authRoleIds(Integer authRoleId) {
        return "authRole:ids:" + authRoleId;
    }

    public static String authRoleKeys(Integer authRoleId) {
        return "authRole:keys:" + authRoleId;
    }
}

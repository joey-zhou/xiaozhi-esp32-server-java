package com.xiaozhi.security.ownership;

/**
 * 资源归属检查器。
 */
public interface OwnershipChecker {

    /**
     * 资源类型标识，例如 role/config/device。
     */
    String getResource();

    /**
     * 校验资源是否归属当前用户，不通过时应抛出业务异常。
     *
     * @param resourceId 资源 ID
     * @param userId     当前登录用户 ID
     */
    void check(Object resourceId, Integer userId);
}

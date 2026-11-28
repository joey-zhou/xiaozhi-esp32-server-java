package com.xiaozhi.security;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.exception.UnauthorizedException;

/**
 * 当前登录用户的 ID，全站只经这里解析。
 * 没登录照旧抛 Sa-Token 的 NotLoginException（401）；登录 ID 为空、越界、不是整数抛 UnauthorizedException。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Integer id() {
        return parse(StpUtil.getLoginId());
    }

    /** Sa-Token 交来的登录 ID 转成用户 ID，规则同 {@link #id()} */
    public static Integer parse(Object loginId) {
        if (loginId == null) {
            throw new UnauthorizedException("无法获取当前登录用户");
        }
        try {
            if (loginId instanceof Number number) {
                return Math.toIntExact(number.longValue());
            }
            return Integer.valueOf(loginId.toString().trim());
        } catch (NumberFormatException | ArithmeticException e) {
            throw new UnauthorizedException("无法获取当前登录用户");
        }
    }
}

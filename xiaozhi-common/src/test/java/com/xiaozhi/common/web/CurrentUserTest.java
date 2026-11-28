package com.xiaozhi.security;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * 登录 ID 到用户 ID 只有这一种解析：Controller、归属切面、权限接口、审计切面口径一致，
 * 解析不出来一律按越权处理，不让 NumberFormatException 漏成「参数错误」或 500。
 */
class CurrentUserTest {

    @Test
    void parsesStringAndNumericLoginIds() {
        assertThat(CurrentUser.parse("7")).isEqualTo(7);
        assertThat(CurrentUser.parse(" 7 ")).isEqualTo(7);
        assertThat(CurrentUser.parse(7)).isEqualTo(7);
        assertThat(CurrentUser.parse(7L)).isEqualTo(7);
    }

    @Test
    void rejectsLoginIdsThatAreNotAUserId() {
        for (Object loginId : new Object[]{null, "", " ", "abc", "99999999999", Long.MAX_VALUE}) {
            assertThatThrownBy(() -> CurrentUser.parse(loginId))
                .as("loginId=%s", loginId)
                .isInstanceOf(UnauthorizedException.class);
        }
    }

    @Test
    void idReadsTheCurrentLogin() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginId).thenReturn("12");

            assertThat(CurrentUser.id()).isEqualTo(12);
        }
    }

    /** 没登录还是 401，不能被吞成越权的 403 */
    @Test
    void notLoggedInStaysNotLogin() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginId)
                .thenThrow(new NotLoginException("未提供token", "login", NotLoginException.NOT_TOKEN));

            assertThatThrownBy(CurrentUser::id).isInstanceOf(NotLoginException.class);
        }
    }
}

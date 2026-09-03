package com.xiaozhi.config.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * 钉住 AI 运行时配置查询端口的用户隔离：listConfigs 必须带用户，
 * 因为底层 listBO 在 userId 为空时会略过用户条件退化成全库查询，取到其他用户的凭据。
 */
class ConfigServiceUserScopeTest {

    @Test
    void listConfigsRejectsMissingUser() {
        ConfigService service = portWithRealDefaults();

        assertThatThrownBy(() -> service.listConfigs(null, "agent", "xingchen", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("查询配置必须指定用户");
        verify(service, never()).listBO(null, "agent", "xingchen", null, null, null);
    }

    @Test
    void listConfigsPassesUserThroughToTheQuery() {
        ConfigService service = portWithRealDefaults();

        service.listConfigs(7, "agent", "xingchen", null, null, null);

        verify(service).listBO(7, "agent", "xingchen", null, null, null);
    }

    /** 只让接口上的 default 方法走真实实现，抽象方法交给 mock */
    private static ConfigService portWithRealDefaults() {
        return mock(ConfigService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }
}

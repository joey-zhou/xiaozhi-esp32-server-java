package com.xiaozhi.agent.service.impl;

import com.xiaozhi.agent.convert.AgentConvert;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.resp.AgentResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import com.xiaozhi.config.infrastructure.convert.ConfigConverter;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.token.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock
    private ConfigService configService;

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private ConfigConverter configConverter;

    @Mock
    private TokenService tokenService;

    @Mock
    private AgentConvert agentConvert;

    @InjectMocks
    private AgentServiceImpl agentService;

    @Test
    void pageReturnsEmptyWhenProviderUnsupported() {
        PageResp<AgentResp> result = agentService.page(1, 10, null, null, 1);

        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verifyNoInteractions(configService, tokenService, agentConvert);
    }

    @Test
    void pageReturnsEmptyWhenDifyConfigsMissing() {
        when(configService.listBO(1, null, "dify", null, null, ConfigBO.STATE_ENABLED)).thenReturn(List.of());

        PageResp<AgentResp> result = agentService.page(1, 10, "  DIFY  ", null, 1);

        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(configService).listBO(1, null, "dify", null, null, ConfigBO.STATE_ENABLED);
    }

    @Test
    void pageBuildsDifyAgentFromExistingLlmConfigWithoutCallingRemoteApi() {
        ConfigBO agentConfig = new ConfigBO();
        agentConfig.setConfigType("agent");
        agentConfig.setApiKey("k1");
        agentConfig.setApiUrl("https://dify.test");
        agentConfig.setProvider("dify");

        ConfigBO llmConfig = new ConfigBO();
        llmConfig.setConfigType("llm");
        llmConfig.setApiKey("k1");
        llmConfig.setProvider("dify");
        llmConfig.setConfigName("现有智能体");
        llmConfig.setConfigDesc("说明");
        llmConfig.setCreateTime(LocalDateTime.now());

        AgentResp resp = new AgentResp();
        resp.setAgentName("现有智能体");
        when(configService.listBO(1, null, "dify", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of(agentConfig, llmConfig));
        when(agentConvert.toResp(org.mockito.ArgumentMatchers.any())).thenReturn(resp);

        PageResp<AgentResp> result = agentService.page(1, 10, "dify", null, 1);

        assertThat(result.getList()).containsExactly(resp);
        assertThat(result.getTotal()).isEqualTo(1);
        verify(configService).listBO(1, null, "dify", null, null, ConfigBO.STATE_ENABLED);
    }
}

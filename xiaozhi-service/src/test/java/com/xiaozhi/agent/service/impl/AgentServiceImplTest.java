package com.xiaozhi.agent.service.impl;

import com.xiaozhi.agent.convert.AgentConvert;
import com.xiaozhi.common.model.bo.AgentBO;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.resp.AgentResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import com.xiaozhi.config.infrastructure.convert.ConfigConverter;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.token.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住智能体列表的装配来源：provider 不支持时直接空列表；DIFY 场景下若本地已有同 apiKey 的
 * llm 配置，必须就地由该配置拼出智能体，不得再走远端 /info 请求。
 */
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
        LocalDateTime createTime = LocalDateTime.of(2026, 1, 2, 3, 4, 5);

        ConfigBO agentConfig = new ConfigBO();
        agentConfig.setConfigType("agent");
        agentConfig.setApiKey("k1");
        agentConfig.setApiUrl("https://dify.test");
        agentConfig.setProvider("dify");

        ConfigBO llmConfig = new ConfigBO();
        llmConfig.setConfigId(66);
        llmConfig.setConfigType("llm");
        llmConfig.setApiKey("k1");
        llmConfig.setProvider("dify");
        llmConfig.setConfigName("现有智能体");
        llmConfig.setConfigDesc("说明");
        llmConfig.setCreateTime(createTime);

        AgentResp resp = new AgentResp();
        when(configService.listBO(1, null, "dify", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of(agentConfig, llmConfig));
        when(agentConvert.toResp(any(AgentBO.class))).thenReturn(resp);

        PageResp<AgentResp> result = agentService.page(1, 10, "dify", null, 1);

        assertThat(result.getList()).containsExactly(resp);
        assertThat(result.getTotal()).isEqualTo(1);
        verify(configService).listBO(1, null, "dify", null, null, ConfigBO.STATE_ENABLED);

        // 断言智能体字段来自本地 llm 配置：一旦回落到 /info 远端分支，名称会变成 "DIFY Agent"
        ArgumentCaptor<AgentBO> captor = ArgumentCaptor.forClass(AgentBO.class);
        verify(agentConvert).toResp(captor.capture());
        AgentBO agent = captor.getValue();
        assertThat(agent.getAgentName()).isEqualTo("现有智能体");
        assertThat(agent.getAgentDesc()).isEqualTo("说明");
        assertThat(agent.getConfigId()).isEqualTo(66);
        assertThat(agent.getApiUrl()).isNull();
        assertThat(agent.getPublishTime()).isEqualTo(Timestamp.valueOf(createTime));
        verifyNoInteractions(configRepository, configConverter, tokenService);
    }
}

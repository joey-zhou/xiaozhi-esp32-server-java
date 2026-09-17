package com.xiaozhi.agent.service.impl;

import com.xiaozhi.agent.convert.AgentConvert;
import com.xiaozhi.common.model.bo.AgentBO;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.port.ProviderTokenClient;
import com.xiaozhi.config.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住智能体列表的装配来源与字段：列表只读已落库的 llm 配置，读的过程中一行都不写；
 * provider 不支持时直接空列表；coze 的 botId 取自配置名；与平台的同步按「用户+平台」节流。
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock
    private ConfigService configService;

    @Mock
    private ProviderTokenClient tokenClient;

    @InjectMocks
    private AgentServiceImpl agentService;

    @BeforeEach
    void setUp() {
        // 用真的 MapStruct 实现：列表返回哪些字段由它决定，换成 mock 就测不到了
        ReflectionTestUtils.setField(agentService, "agentConvert", Mappers.getMapper(AgentConvert.class));
    }

    @Test
    void pageReturnsEmptyWhenProviderUnsupported() {
        PageResult<AgentBO> result = agentService.page(1, 10, null, null, 1);

        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verifyNoInteractions(configService, tokenClient);
    }

    @Test
    void pageBuildsEveryListedFieldFromTheStoredConfig() {
        LocalDateTime createTime = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        LocalDateTime updateTime = LocalDateTime.of(2026, 2, 3, 4, 5, 6);
        ConfigBO stored = new ConfigBO()
            .setConfigId(66)
            .setUserId(1)
            .setConfigName("现有智能体")
            .setConfigDesc("说明")
            .setConfigType("llm")
            .setModelType("chat")
            .setProvider("dify")
            .setAppId("app-1")
            .setApiKey("k1")
            .setApiUrl("https://dify.test")
            .setState(ConfigBO.STATE_ENABLED)
            .setIsDefault(ConfigBO.DEFAULT_YES)
            .setCreateTime(createTime)
            .setUpdateTime(updateTime);

        when(configService.listBO(1, "llm", "dify", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of(stored));
        // 同步那一路查的是「平台凭据 + 已落库的智能体」，这里只有后者，没有要补的
        when(configService.listBO(1, null, "dify", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of(stored));

        PageResult<AgentBO> result = agentService.page(1, 10, "  DIFY  ", null, 1);

        assertThat(result.getTotal()).isEqualTo(1);
        AgentBO agent = result.getList().get(0);
        assertThat(agent.getConfigId()).isEqualTo(66);
        assertThat(agent.getUserId()).isEqualTo(1);
        assertThat(agent.getConfigName()).isEqualTo("现有智能体");
        assertThat(agent.getConfigDesc()).isEqualTo("说明");
        assertThat(agent.getConfigType()).isEqualTo("llm");
        assertThat(agent.getModelType()).isEqualTo("chat");
        assertThat(agent.getProvider()).isEqualTo("dify");
        assertThat(agent.getAppId()).isEqualTo("app-1");
        assertThat(agent.getApiUrl()).isEqualTo("https://dify.test");
        assertThat(agent.getState()).isEqualTo(ConfigBO.STATE_ENABLED);
        assertThat(agent.getIsDefault()).isEqualTo(ConfigBO.DEFAULT_YES);
        assertThat(agent.getAgentName()).isEqualTo("现有智能体");
        assertThat(agent.getAgentDesc()).isEqualTo("说明");
        assertThat(agent.getCreateTime()).isEqualTo(createTime);
        assertThat(agent.getUpdateTime()).isEqualTo(updateTime);
        // 平台没给发布时间时由配置创建时间兜底
        assertThat(agent.getPublishTime()).isEqualTo(Timestamp.valueOf(createTime));
        // 读列表不写库
        verify(configService, never()).saveAgentModel(any());
    }

    @Test
    void pageMarksCozeBotIdWithTheStoredConfigName() {
        ConfigBO stored = new ConfigBO()
            .setConfigId(3)
            .setConfigName("bot-1")
            .setConfigDesc("导购")
            .setConfigType("llm")
            .setProvider("coze")
            .setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(configService.listBO(1, "llm", "coze", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of(stored));
        // 没配平台凭据，同步这一路直接结束
        when(configService.listBO(1, "agent", "coze", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of());

        PageResult<AgentBO> result = agentService.page(1, 10, "coze", null, 1);

        AgentBO agent = result.getList().get(0);
        assertThat(agent.getBotId()).isEqualTo("bot-1");
        // 平台侧快照拿不到时，名称回落到配置名（也就是 botId）
        assertThat(agent.getAgentName()).isEqualTo("bot-1");
        // 没有平台凭据就不会去要 token，也不会写库
        verifyNoInteractions(tokenClient);
        verify(configService, never()).saveAgentModel(any());
    }

    @Test
    void pageFiltersByAgentNameIgnoringCase() {
        ConfigBO matched = new ConfigBO()
            .setConfigId(1)
            .setConfigName("Sales Bot")
            .setConfigType("llm")
            .setProvider("xingchen")
            .setApiKey("k1");
        ConfigBO other = new ConfigBO()
            .setConfigId(2)
            .setConfigName("客服")
            .setConfigType("llm")
            .setProvider("xingchen")
            .setApiKey("k2");

        when(configService.listBO(1, "llm", "xingchen", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of(matched, other));
        when(configService.listBO(1, null, "xingchen", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of(matched, other));

        PageResult<AgentBO> result = agentService.page(1, 10, "xingchen", "sales", 1);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList().get(0).getAgentName()).isEqualTo("Sales Bot");
    }

    @Test
    void pageSyncsWithThePlatformOnlyOncePerThrottleWindow() {
        ConfigBO stored = new ConfigBO()
            .setConfigId(3)
            .setConfigName("bot-1")
            .setConfigType("llm")
            .setProvider("coze");

        when(configService.listBO(1, "llm", "coze", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of(stored));
        when(configService.listBO(1, "agent", "coze", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of());

        agentService.page(1, 10, "coze", null, 1);
        agentService.page(2, 10, "coze", null, 1);

        // 翻第二页不再向平台同步：取平台凭据只发生在第一次，列表本身每次都读库
        verify(configService, times(1)).listBO(1, "agent", "coze", null, null, ConfigBO.STATE_ENABLED);
    }

    @Test
    void pageSyncsEveryTimeWhileNothingIsStoredYet() {
        when(configService.listBO(1, "llm", "coze", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of());
        when(configService.listBO(1, "agent", "coze", null, null, ConfigBO.STATE_ENABLED))
            .thenReturn(List.of());

        agentService.page(1, 10, "coze", null, 1);
        agentService.page(1, 10, "coze", null, 1);

        // 刚配好平台凭据的场景：库里还没有智能体时不受节流限制，否则要等节流窗口过去才看得见
        verify(configService, times(2)).listBO(1, "agent", "coze", null, null, ConfigBO.STATE_ENABLED);
    }
}

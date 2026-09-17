package com.xiaozhi.config;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.common.model.resp.ConfigResp;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import com.xiaozhi.config.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住配置写接口的出参：字段全部来自落库后的聚合根，存完不再回查配置表。
 * <p>回读那一版是「写完再 getBO」，缓存刚被淘汰，每次创建/修改配置都要多打一次库；
 * 换成聚合根出参后，出参字段与默认值必须与回读那一版逐个一致。
 */
@ExtendWith(MockitoExtension.class)
class ConfigAppServiceWriteRespTest {

    private static final int CONFIG_ID = 11;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 8, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 9, 5, 12, 0);

    @Mock
    private ConfigService configService;

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private ConfigChangePolicy configChangePolicy;

    private final ConfigConvert configConvert = Mappers.getMapper(ConfigConvert.class);

    private ConfigAppService configAppService;

    @BeforeEach
    void setUp() {
        configAppService = new ConfigAppService();
        ReflectionTestUtils.setField(configAppService, "configService", configService);
        ReflectionTestUtils.setField(configAppService, "configConvert", configConvert);
        ReflectionTestUtils.setField(configAppService, "configRepository", configRepository);
        ReflectionTestUtils.setField(configAppService, "configChangePolicy", configChangePolicy);
    }

    @Test
    void createReturnsEveryResponseFieldFromTheAggregate() {
        stampOnSave(CREATED_AT, CREATED_AT);

        ConfigResp resp = configAppService.create(createReq(), 7, false);

        assertThat(resp.getConfigId()).isEqualTo(CONFIG_ID);
        assertThat(resp.getUserId()).isEqualTo(7);
        assertThat(resp.getConfigName()).isEqualTo("通义千问");
        assertThat(resp.getConfigDesc()).isEqualTo("对话模型");
        assertThat(resp.getConfigType()).isEqualTo("llm");
        assertThat(resp.getModelType()).isEqualTo("chat");
        assertThat(resp.getProvider()).isEqualTo("aliyun");
        assertThat(resp.getAppId()).isEqualTo("app-1");
        assertThat(resp.getApiUrl()).isEqualTo("https://api.test/v1");
        assertThat(resp.getEnableThinking()).isTrue();
        // 新建的配置一律启用、非默认，与回读那一版落库后再查出来的取值一致
        assertThat(resp.getState()).isEqualTo(ConfigBO.STATE_ENABLED);
        assertThat(resp.getIsDefault()).isEqualTo(ConfigBO.DEFAULT_NO);
        assertThat(resp.getCreateTime()).isEqualTo(CREATED_AT);
        assertThat(resp.getUpdateTime()).isEqualTo(CREATED_AT);
        // 出参全部来自聚合根：创建路径一次配置表都不读
        verify(configService, never()).getBO(any());
    }

    @Test
    void updateReturnsPatchedFieldsAndKeepsUntouchedOnes() {
        when(configRepository.findById(CONFIG_ID)).thenReturn(Optional.of(storedAggregate()));
        stampOnSave(null, UPDATED_AT);

        ConfigUpdateReq req = new ConfigUpdateReq();
        req.setConfigName("改过的名字");
        req.setIsDefault(ConfigBO.DEFAULT_YES);

        ConfigResp resp = configAppService.update(CONFIG_ID, req, true);

        assertThat(resp.getConfigId()).isEqualTo(CONFIG_ID);
        assertThat(resp.getUserId()).isEqualTo(7);
        assertThat(resp.getConfigName()).isEqualTo("改过的名字");
        assertThat(resp.getIsDefault()).isEqualTo(ConfigBO.DEFAULT_YES);
        // 本次没带的字段保留库里那份，不能因为不回读就变成 null
        assertThat(resp.getConfigDesc()).isEqualTo("对话模型");
        assertThat(resp.getConfigType()).isEqualTo("llm");
        assertThat(resp.getModelType()).isEqualTo("chat");
        assertThat(resp.getProvider()).isEqualTo("aliyun");
        assertThat(resp.getAppId()).isEqualTo("app-1");
        assertThat(resp.getApiUrl()).isEqualTo("https://api.test/v1");
        assertThat(resp.getEnableThinking()).isTrue();
        assertThat(resp.getState()).isEqualTo(ConfigBO.STATE_ENABLED);
        assertThat(resp.getCreateTime()).isEqualTo(CREATED_AT);
        // 更新时间取本次写库实际落的值，不是聚合根加载时那份
        assertThat(resp.getUpdateTime()).isEqualTo(UPDATED_AT);
        // 配置只经聚合根读一次（前置校验用它的快照），写完不再回读
        verify(configRepository, times(1)).findById(CONFIG_ID);
        verify(configService, never()).getBO(any());
    }

    /** 模拟仓储 save()：自增主键与自动填充的时间戳在落库后被回填进聚合根 */
    private void stampOnSave(LocalDateTime createTime, LocalDateTime updateTime) {
        doAnswer(invocation -> {
            AiConfig config = invocation.getArgument(0, AiConfig.class);
            if (config.getConfigId() == null) {
                config.assignId(CONFIG_ID);
            }
            config.markPersisted(createTime, updateTime);
            return null;
        }).when(configRepository).save(any(AiConfig.class));
    }

    private static ConfigCreateReq createReq() {
        ConfigCreateReq req = new ConfigCreateReq();
        req.setConfigName("通义千问");
        req.setConfigDesc("对话模型");
        req.setConfigType("llm");
        req.setModelType("chat");
        req.setProvider("aliyun");
        req.setAppId("app-1");
        req.setApiKey("key");
        req.setApiUrl("https://api.test/v1");
        req.setEnableThinking(true);
        return req;
    }

    /** 库里已有的一条配置，字段全带值，用于钉住局部更新的出参一个字段都不少 */
    private static AiConfig storedAggregate() {
        return AiConfig.reconstitute(CONFIG_ID, 7, "llm", "aliyun", "通义千问", "对话模型", "chat",
            "app-1", "key", "secret", null, null, "https://api.test/v1", true,
            AiConfig.STATE_ENABLED, false, CREATED_AT, LocalDateTime.of(2026, 2, 1, 8, 0));
    }
}

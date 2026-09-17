package com.xiaozhi.config;

import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住 create/update/delete 会先过 ConfigChangePolicy 的前置校验，校验抛异常时不写库；
 * 具体每条护栏规则的通过/拒绝由 ConfigChangePolicyTest 钉住。
 */
@ExtendWith(MockitoExtension.class)
class ConfigAppServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private ConfigConvert configConvert;

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private ConfigChangePolicy configChangePolicy;

    private ConfigAppService service;

    @BeforeEach
    void setUp() {
        service = new ConfigAppService();
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "configConvert", configConvert);
        ReflectionTestUtils.setField(service, "configRepository", configRepository);
        ReflectionTestUtils.setField(service, "configChangePolicy", configChangePolicy);
    }

    private static ConfigBO embeddingBO(boolean isDefault) {
        ConfigBO bo = new ConfigBO();
        bo.setConfigId(1);
        bo.setConfigType("llm");
        bo.setModelType(ConfigBO.ModelType.embedding.getValue());
        bo.setProvider("openai");
        bo.setIsDefault(isDefault ? ConfigBO.DEFAULT_YES : ConfigBO.DEFAULT_NO);
        return bo;
    }

    @Test
    void createRejectedWhenPolicyThrows() {
        ConfigCreateReq req = new ConfigCreateReq();
        doThrow(new OperationFailedException("当前对象存储上还有历史文件"))
                .when(configChangePolicy).checkCreate(req, true);

        assertThatThrownBy(() -> service.create(req, 1, true))
                .isInstanceOf(OperationFailedException.class);
        verify(configRepository, never()).save(any());
    }

    @Test
    void createSucceedsWhenPolicyPasses() {
        ConfigCreateReq req = new ConfigCreateReq();
        req.setModelType(ConfigBO.ModelType.embedding.getValue());
        req.setIsDefault(ConfigBO.DEFAULT_YES);
        doNothing().when(configChangePolicy).checkCreate(req, true);
        when(configConvert.toBO(req)).thenReturn(embeddingBO(true));
        when(configConvert.toResp(any(AiConfig.class))).thenReturn(new ConfigResp());

        ConfigResp resp = service.create(req, 1, true);

        assertThat(resp).isNotNull();
        verify(configChangePolicy).checkCreate(req, true);
    }

    @Test
    void updateRejectedWhenPolicyThrows() {
        ConfigUpdateReq req = new ConfigUpdateReq();
        req.setIsDefault(ConfigBO.DEFAULT_YES);
        when(configRepository.findById(1)).thenReturn(Optional.of(AiConfig.newConfig(1, embeddingBO(false))));
        doThrow(new OperationFailedException("当前对象存储上还有历史文件"))
                .when(configChangePolicy).checkUpdate(any(ConfigBO.class), eq(req), eq(true));

        assertThatThrownBy(() -> service.update(1, req, true))
                .isInstanceOf(OperationFailedException.class);
        verify(configRepository, never()).save(any());
    }

    @Test
    void updateSucceedsWhenPolicyPasses() {
        ConfigBO existing = embeddingBO(false);
        ConfigUpdateReq req = new ConfigUpdateReq();
        req.setIsDefault(ConfigBO.DEFAULT_YES);
        when(configRepository.findById(1)).thenReturn(Optional.of(AiConfig.newConfig(1, existing)));
        when(configConvert.toBO(req)).thenReturn(embeddingBO(true));
        when(configConvert.toResp(any(AiConfig.class))).thenReturn(new ConfigResp());
        doNothing().when(configRepository).save(any(AiConfig.class));

        ConfigResp resp = service.update(1, req, true);

        assertThat(resp).isNotNull();
        // 拦截规则拿到的是合并补丁之前的快照，并且配置只经聚合根读一次
        ArgumentCaptor<ConfigBO> current = ArgumentCaptor.forClass(ConfigBO.class);
        verify(configChangePolicy).checkUpdate(current.capture(), eq(req), eq(true));
        assertThat(current.getValue().getModelType()).isEqualTo(ConfigBO.ModelType.embedding.getValue());
        assertThat(current.getValue().getIsDefault()).isEqualTo(ConfigBO.DEFAULT_NO);
        verify(configService, never()).getBO(any());
    }

    @Test
    void updateMissingConfigSkipsPolicy() {
        when(configRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1, new ConfigUpdateReq(), true))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(configChangePolicy, never()).checkUpdate(any(), any(), anyBoolean());
    }

    @Test
    void deleteRejectedWhenPolicyThrows() {
        ConfigBO existing = embeddingBO(false);
        when(configService.getBO(1)).thenReturn(existing);
        doThrow(new OperationFailedException("当前对象存储上还有历史文件"))
                .when(configChangePolicy).checkDelete(existing, true);

        assertThatThrownBy(() -> service.delete(1, true))
                .isInstanceOf(OperationFailedException.class);
        verify(configRepository, never()).delete(any());
    }

    @Test
    void deleteSucceedsWhenPolicyPasses() {
        ConfigBO existing = embeddingBO(false);
        when(configService.getBO(1)).thenReturn(existing);
        doNothing().when(configChangePolicy).checkDelete(existing, true);

        service.delete(1, true);

        verify(configChangePolicy).checkDelete(existing, true);
        verify(configRepository).delete(1);
    }
}

package com.xiaozhi.config;

import com.xiaozhi.common.exception.ConfirmRequiredException;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.storage.service.StorageReferenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住换对象存储前的存量可达性校验：库里存的地址没有归属信息也没有迁移机制，
 * 换掉当前生效的对象存储会让老地址永久解析不出来，因此有存量时必须先让用户确认。
 */
@ExtendWith(MockitoExtension.class)
class ConfigAppServiceStorageSwitchTest {

    private static final int CONFIG_ID = 11;

    @Mock
    private ConfigService configService;

    @Mock
    private ConfigConvert configConvert;

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private StorageReferenceService storageReferenceService;

    @InjectMocks
    private ConfigAppService configAppService;

    @Test
    void switchingDefaultOssConfigIsRejectedWhileFilesRemain() {
        when(configService.getBO(CONFIG_ID)).thenReturn(ossConfig(ConfigBO.DEFAULT_NO));
        when(storageReferenceService.countOnCurrentStorage()).thenReturn(1234L);

        assertThatThrownBy(() -> configAppService.update(CONFIG_ID, setDefault(), false))
            .isInstanceOf(ConfirmRequiredException.class)
            .hasMessageContaining("1234");

        verify(configRepository, never()).save(any());
    }

    @Test
    void switchingDefaultOssConfigProceedsWhenNothingIsStoredThere() {
        givenExistingOssConfig(ConfigBO.DEFAULT_NO);
        when(storageReferenceService.countOnCurrentStorage()).thenReturn(0L);

        assertThatCode(() -> configAppService.update(CONFIG_ID, setDefault(), false))
            .doesNotThrowAnyException();

        verify(configRepository).save(any());
    }

    @Test
    void confirmedSwitchSkipsTheCountEntirely() {
        givenExistingOssConfig(ConfigBO.DEFAULT_NO);

        assertThatCode(() -> configAppService.update(CONFIG_ID, setDefault(), true))
            .doesNotThrowAnyException();

        verify(configRepository).save(any());
        verifyNoInteractions(storageReferenceService);
    }

    @Test
    void editingTheDefaultBucketIsRejectedWhileFilesRemain() {
        when(configService.getBO(CONFIG_ID)).thenReturn(ossConfig(ConfigBO.DEFAULT_YES));
        when(storageReferenceService.countOnCurrentStorage()).thenReturn(5L);

        ConfigUpdateReq req = new ConfigUpdateReq();
        req.setConfigName("另一个桶");

        assertThatThrownBy(() -> configAppService.update(CONFIG_ID, req, false))
            .isInstanceOf(ConfirmRequiredException.class);
    }

    @Test
    void rotatingCredentialsOfTheDefaultKeepsTheSamePrefixSoNothingIsCounted() {
        givenExistingOssConfig(ConfigBO.DEFAULT_YES);

        ConfigUpdateReq req = new ConfigUpdateReq();
        req.setAk("new-ak");
        req.setSk("new-sk");

        assertThatCode(() -> configAppService.update(CONFIG_ID, req, false))
            .doesNotThrowAnyException();

        verifyNoInteractions(storageReferenceService);
    }

    @Test
    void newOssConfigCreatedAsDefaultIsRejectedWhileFilesRemain() {
        when(storageReferenceService.countOnCurrentStorage()).thenReturn(7L);

        ConfigCreateReq req = new ConfigCreateReq();
        req.setConfigType("oss");
        req.setConfigName("new-bucket");
        req.setProvider("aliyun");
        req.setIsDefault(ConfigBO.DEFAULT_YES);

        assertThatThrownBy(() -> configAppService.create(req, 1, false))
            .isInstanceOf(ConfirmRequiredException.class)
            .hasMessageContaining("7");

        verify(configRepository, never()).save(any());
    }

    @Test
    void deletingTheActiveOssConfigIsRejectedWhileFilesRemain() {
        // 删掉当前默认那条等于把存储切回本地，与「默认让位」是同一件事，判定口径必须一致
        when(configService.getBO(CONFIG_ID)).thenReturn(ossConfig(ConfigBO.DEFAULT_YES));
        when(storageReferenceService.countOnCurrentStorage()).thenReturn(42L);

        assertThatThrownBy(() -> configAppService.delete(CONFIG_ID, false))
            .isInstanceOf(ConfirmRequiredException.class)
            .hasMessageContaining("42");

        verify(configRepository, never()).delete(any());
    }

    @Test
    void confirmedDeleteSkipsTheCountEntirely() {
        when(configService.getBO(CONFIG_ID)).thenReturn(ossConfig(ConfigBO.DEFAULT_YES));

        assertThatCode(() -> configAppService.delete(CONFIG_ID, true)).doesNotThrowAnyException();

        verify(configRepository).delete(CONFIG_ID);
        verifyNoInteractions(storageReferenceService);
    }

    @Test
    void deletingANonDefaultOssConfigNeverCountsAnything() {
        // 非默认那条不决定当前生效的存储，删掉不影响任何历史地址
        when(configService.getBO(CONFIG_ID)).thenReturn(ossConfig(ConfigBO.DEFAULT_NO));

        assertThatCode(() -> configAppService.delete(CONFIG_ID, false)).doesNotThrowAnyException();

        verify(configRepository).delete(CONFIG_ID);
        verifyNoInteractions(storageReferenceService);
    }

    private void givenExistingOssConfig(String isDefault) {
        when(configService.getBO(CONFIG_ID)).thenReturn(ossConfig(isDefault));
        when(configRepository.findById(CONFIG_ID)).thenReturn(Optional.of(existingAggregate(isDefault)));
        when(configConvert.toBO(any(ConfigUpdateReq.class))).thenReturn(new ConfigBO());
    }

    /** 非默认改成默认，即把当前生效的存储换到这条配置上 */
    private static ConfigUpdateReq setDefault() {
        ConfigUpdateReq req = new ConfigUpdateReq();
        req.setIsDefault(ConfigBO.DEFAULT_YES);
        return req;
    }

    private static ConfigBO ossConfig(String isDefault) {
        return new ConfigBO()
            .setConfigId(CONFIG_ID)
            .setConfigType("oss")
            .setProvider("tencent")
            .setConfigName("bucket")
            .setAppId("ap-beijing")
            .setIsDefault(isDefault);
    }

    private static AiConfig existingAggregate(String isDefault) {
        return AiConfig.reconstitute(CONFIG_ID, 1, "oss", "tencent", "bucket", null, null,
            "ap-beijing", "key", "secret", null, null, null, null,
            AiConfig.STATE_ENABLED, ConfigBO.DEFAULT_YES.equals(isDefault), null, null);
    }
}

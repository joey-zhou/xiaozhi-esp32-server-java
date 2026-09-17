package com.xiaozhi.config;

import com.xiaozhi.common.exception.ConfirmRequiredException;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.storage.service.StorageReferenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住配置写前的护栏：换对象存储（新建默认、切默认、让位、改地址前缀、删除默认）
 * 未确认且当前存储上还有历史引用→拒绝。
 */
@ExtendWith(MockitoExtension.class)
class ConfigChangePolicyTest {

    private static final int CONFIG_ID = 11;

    @Mock
    private StorageReferenceService storageReferenceService;

    @InjectMocks
    private ConfigChangePolicy policy;

    // ---------- 新建默认 oss 配置 ----------

    @Test
    void creatingDefaultOssConfigIsRejectedWhileFilesRemain() {
        when(storageReferenceService.countOnCurrentStorage()).thenReturn(7L);
        ConfigCreateReq req = new ConfigCreateReq();
        req.setConfigType("oss");
        req.setIsDefault(ConfigBO.DEFAULT_YES);

        assertThatThrownBy(() -> policy.checkCreate(req, false))
                .isInstanceOf(ConfirmRequiredException.class)
                .hasMessageContaining("7");
    }

    @Test
    void creatingNonDefaultOssConfigNeverCountsAnything() {
        ConfigCreateReq req = new ConfigCreateReq();
        req.setConfigType("oss");
        req.setIsDefault(ConfigBO.DEFAULT_NO);

        assertThatCode(() -> policy.checkCreate(req, false)).doesNotThrowAnyException();

        verifyNoInteractions(storageReferenceService);
    }

    // ---------- 换对象存储（更新路径：切默认、让位、改地址前缀） ----------

    @Test
    void switchingDefaultOssConfigIsRejectedWhileFilesRemain() {
        when(storageReferenceService.countOnCurrentStorage()).thenReturn(1234L);

        assertThatThrownBy(() -> policy.checkUpdate(ossConfig(ConfigBO.DEFAULT_NO), setDefault(), false))
                .isInstanceOf(ConfirmRequiredException.class)
                .hasMessageContaining("1234");
    }

    @Test
    void switchingDefaultOssConfigProceedsWhenNothingIsStoredThere() {
        when(storageReferenceService.countOnCurrentStorage()).thenReturn(0L);

        assertThatCode(() -> policy.checkUpdate(ossConfig(ConfigBO.DEFAULT_NO), setDefault(), false))
                .doesNotThrowAnyException();
    }

    @Test
    void confirmedSwitchSkipsTheCountEntirely() {
        assertThatCode(() -> policy.checkUpdate(ossConfig(ConfigBO.DEFAULT_NO), setDefault(), true))
                .doesNotThrowAnyException();

        verifyNoInteractions(storageReferenceService);
    }

    @Test
    void editingTheDefaultBucketIsRejectedWhileFilesRemain() {
        when(storageReferenceService.countOnCurrentStorage()).thenReturn(5L);

        ConfigUpdateReq req = new ConfigUpdateReq();
        req.setConfigName("另一个桶");

        assertThatThrownBy(() -> policy.checkUpdate(ossConfig(ConfigBO.DEFAULT_YES), req, false))
                .isInstanceOf(ConfirmRequiredException.class);
    }

    @Test
    void rotatingCredentialsOfTheDefaultKeepsTheSamePrefixSoNothingIsCounted() {
        ConfigUpdateReq req = new ConfigUpdateReq();
        req.setAk("new-ak");
        req.setSk("new-sk");

        assertThatCode(() -> policy.checkUpdate(ossConfig(ConfigBO.DEFAULT_YES), req, false))
                .doesNotThrowAnyException();

        verifyNoInteractions(storageReferenceService);
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

    // ---------- 换对象存储（删除路径：删掉当前默认） ----------

    @Test
    void deletingTheActiveOssConfigIsRejectedWhileFilesRemain() {
        // 删掉当前默认那条等于把存储切回本地，与「默认让位」是同一件事，判定口径必须一致
        when(storageReferenceService.countOnCurrentStorage()).thenReturn(42L);

        assertThatThrownBy(() -> policy.checkDelete(ossConfig(ConfigBO.DEFAULT_YES), false))
                .isInstanceOf(ConfirmRequiredException.class)
                .hasMessageContaining("42");
    }

    @Test
    void confirmedDeleteSkipsTheCountEntirely() {
        assertThatCode(() -> policy.checkDelete(ossConfig(ConfigBO.DEFAULT_YES), true))
                .doesNotThrowAnyException();

        verifyNoInteractions(storageReferenceService);
    }

    @Test
    void deletingANonDefaultOssConfigNeverCountsAnything() {
        // 非默认那条不决定当前生效的存储，删掉不影响任何历史地址
        assertThatCode(() -> policy.checkDelete(ossConfig(ConfigBO.DEFAULT_NO), false))
                .doesNotThrowAnyException();

        verifyNoInteractions(storageReferenceService);
    }
}

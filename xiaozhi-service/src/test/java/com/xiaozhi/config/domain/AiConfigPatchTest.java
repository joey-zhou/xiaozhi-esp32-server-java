package com.xiaozhi.config.domain;

import com.xiaozhi.common.model.bo.ConfigBO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住聚合根的字段合并规则：只有 null 算「未提供」，update 与 mergePatch 共用同一条判定；
 * 身份与时间戳不可被 patch 改写；默认标记变更必须发出 DEFAULT_CHANGED。
 */
class AiConfigPatchTest {

    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime UPDATE_TIME = LocalDateTime.of(2026, 1, 2, 0, 0);

    @Test
    void mergePatchTakesSavedValueForFieldsNotProvided() {
        AiConfig config = savedConfig(true);

        ConfigBO merged = config.mergePatch(new ConfigBO().setApiUrl("https://form.example.com"));

        assertThat(merged.getApiUrl()).isEqualTo("https://form.example.com");
        assertThat(merged.getApiKey()).isEqualTo("saved-key");
        assertThat(merged.getApiSecret()).isEqualTo("saved-secret");
        assertThat(merged.getAk()).isEqualTo("saved-ak");
        assertThat(merged.getSk()).isEqualTo("saved-sk");
        assertThat(merged.getAppId()).isEqualTo("saved-app");
        assertThat(merged.getConfigName()).isEqualTo("我的模型");
        assertThat(merged.getConfigDesc()).isEqualTo("备注");
        assertThat(merged.getConfigType()).isEqualTo("llm");
        assertThat(merged.getModelType()).isEqualTo("chat");
        assertThat(merged.getProvider()).isEqualTo("aliyun");
        assertThat(merged.getState()).isEqualTo(AiConfig.STATE_ENABLED);
        assertThat(merged.getEnableThinking()).isFalse();
        assertThat(merged.getIsDefault()).isEqualTo(ConfigBO.DEFAULT_YES);
    }

    // 不带任何字段的补丁等于取整份快照，调用方靠这条原样取出库里保存的配置
    @Test
    void mergePatchWithoutAnyFieldReturnsTheWholeSavedConfig() {
        AiConfig config = disabledDefaultConfig();

        ConfigBO snapshot = config.mergePatch(new ConfigBO());

        assertThat(snapshot.getConfigId()).isEqualTo(7);
        assertThat(snapshot.getUserId()).isEqualTo(9);
        assertThat(snapshot.getConfigName()).isEqualTo("我的模型");
        assertThat(snapshot.getConfigDesc()).isEqualTo("备注");
        assertThat(snapshot.getConfigType()).isEqualTo("llm");
        assertThat(snapshot.getModelType()).isEqualTo("chat");
        assertThat(snapshot.getProvider()).isEqualTo("aliyun");
        assertThat(snapshot.getAppId()).isEqualTo("saved-app");
        assertThat(snapshot.getApiKey()).isEqualTo("saved-key");
        assertThat(snapshot.getApiSecret()).isEqualTo("saved-secret");
        assertThat(snapshot.getAk()).isEqualTo("saved-ak");
        assertThat(snapshot.getSk()).isEqualTo("saved-sk");
        assertThat(snapshot.getApiUrl()).isEqualTo("https://saved.example.com");
        assertThat(snapshot.getState()).isEqualTo(AiConfig.STATE_DISABLED);
        assertThat(snapshot.getIsDefault()).isEqualTo(ConfigBO.DEFAULT_YES);
        assertThat(snapshot.getEnableThinking()).isTrue();
        assertThat(snapshot.getCreateTime()).isEqualTo(CREATE_TIME);
        assertThat(snapshot.getUpdateTime()).isEqualTo(UPDATE_TIME);
    }

    @Test
    void mergePatchOverridesEveryFieldProvidedByPatch() {
        AiConfig config = savedConfig(true);

        ConfigBO merged = config.mergePatch(new ConfigBO()
                .setConfigName("form-name")
                .setConfigDesc("form-desc")
                .setConfigType("stt")
                .setModelType("vision")
                .setProvider("openai")
                .setAppId("form-app")
                .setApiKey("form-key")
                .setApiSecret("form-secret")
                .setAk("form-ak")
                .setSk("form-sk")
                .setApiUrl("https://form.example.com")
                .setState(AiConfig.STATE_DISABLED)
                .setEnableThinking(true)
                .setIsDefault(ConfigBO.DEFAULT_NO));

        assertThat(merged.getConfigName()).isEqualTo("form-name");
        assertThat(merged.getConfigDesc()).isEqualTo("form-desc");
        assertThat(merged.getConfigType()).isEqualTo("stt");
        assertThat(merged.getModelType()).isEqualTo("vision");
        assertThat(merged.getProvider()).isEqualTo("openai");
        assertThat(merged.getAppId()).isEqualTo("form-app");
        assertThat(merged.getApiKey()).isEqualTo("form-key");
        assertThat(merged.getApiSecret()).isEqualTo("form-secret");
        assertThat(merged.getAk()).isEqualTo("form-ak");
        assertThat(merged.getSk()).isEqualTo("form-sk");
        assertThat(merged.getApiUrl()).isEqualTo("https://form.example.com");
        assertThat(merged.getState()).isEqualTo(AiConfig.STATE_DISABLED);
        assertThat(merged.getEnableThinking()).isTrue();
        assertThat(merged.getIsDefault()).isEqualTo(ConfigBO.DEFAULT_NO);
    }

    @Test
    void mergePatchKeepsIdentityAndTimestampsOfTheAggregate() {
        AiConfig config = savedConfig(true);

        ConfigBO merged = config.mergePatch(new ConfigBO()
                .setConfigId(999)
                .setUserId(1)
                .setCreateTime(LocalDateTime.of(2000, 1, 1, 0, 0))
                .setUpdateTime(LocalDateTime.of(2000, 1, 1, 0, 0)));

        assertThat(merged.getConfigId()).isEqualTo(7);
        assertThat(merged.getUserId()).isEqualTo(9);
        assertThat(merged.getCreateTime()).isEqualTo(CREATE_TIME);
        assertThat(merged.getUpdateTime()).isEqualTo(UPDATE_TIME);
    }

    @Test
    void mergePatchLeavesAggregateUnchangedAndSilent() {
        AiConfig config = savedConfig(true);

        config.mergePatch(new ConfigBO().setApiKey("form-key").setIsDefault(ConfigBO.DEFAULT_NO));

        assertThat(config.getApiKey()).isEqualTo("saved-key");
        assertThat(config.isDefault()).isTrue();
        assertThat(config.pullSignals()).isEmpty();
    }

    @Test
    void updateAppliesTheSameMissingFieldRule() {
        AiConfig config = savedConfig(true);

        config.update(new ConfigBO()
                .setApiKey(null)
                .setApiUrl("https://form.example.com"));

        assertThat(config.getApiKey()).isEqualTo("saved-key");
        assertThat(config.getApiUrl()).isEqualTo("https://form.example.com");
        assertThat(config.getConfigName()).isEqualTo("我的模型");
        assertThat(config.isDefault()).isTrue();
        assertThat(config.pullSignals()).containsExactly(AiConfig.DomainSignal.UPDATED);
    }

    @Test
    void updateSignalsDefaultChangedWhenConfigTurnsDefault() {
        AiConfig config = savedConfig(false);

        config.update(new ConfigBO().setIsDefault(ConfigBO.DEFAULT_YES));

        assertThat(config.isDefault()).isTrue();
        assertThat(config.pullSignals())
                .containsExactly(AiConfig.DomainSignal.DEFAULT_CHANGED, AiConfig.DomainSignal.UPDATED);
    }

    @Test
    void updateStaysSilentWhenDefaultIsAlreadySet() {
        AiConfig config = savedConfig(true);

        config.update(new ConfigBO().setIsDefault(ConfigBO.DEFAULT_YES));

        assertThat(config.isDefault()).isTrue();
        assertThat(config.pullSignals()).containsExactly(AiConfig.DomainSignal.UPDATED);
    }

    @Test
    void updateClearsDefaultWithoutSignalWhenPatchTurnsItOff() {
        AiConfig config = savedConfig(true);

        config.update(new ConfigBO().setIsDefault(ConfigBO.DEFAULT_NO));

        assertThat(config.isDefault()).isFalse();
        assertThat(config.pullSignals()).containsExactly(AiConfig.DomainSignal.UPDATED);
    }

    @Test
    void updateKeepsDefaultFlagWhenPatchOmitsIt() {
        AiConfig config = savedConfig(false);

        config.update(new ConfigBO().setConfigName("form-name"));

        assertThat(config.isDefault()).isFalse();
        assertThat(config.pullSignals()).containsExactly(AiConfig.DomainSignal.UPDATED);
    }

    private static AiConfig disabledDefaultConfig() {
        return AiConfig.reconstitute(7, 9, "llm", "aliyun",
                "我的模型", "备注", "chat",
                "saved-app", "saved-key", "saved-secret",
                "saved-ak", "saved-sk", "https://saved.example.com",
                true, null, AiConfig.STATE_DISABLED, true,
                CREATE_TIME, UPDATE_TIME);
    }

    private static AiConfig savedConfig(boolean isDefault) {
        return AiConfig.reconstitute(7, 9, "llm", "aliyun",
                "我的模型", "备注", "chat",
                "saved-app", "saved-key", "saved-secret",
                "saved-ak", "saved-sk", "https://saved.example.com",
                false, null, AiConfig.STATE_ENABLED, isDefault,
                CREATE_TIME, UPDATE_TIME);
    }
}

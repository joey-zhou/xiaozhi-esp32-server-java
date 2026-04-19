package com.xiaozhi.config.domain;

import com.xiaozhi.common.model.bo.ConfigBO;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AiConfig 聚合根 —— 表示一条 AI 模型配置（LLM / TTS / STT / VAD / Embedding 等）。
 * <p>
 * 不变式：同一 userId + configType + modelType 组合下最多一条默认配置（由 ConfigRepository.save 维护）。
 */
@Getter
public class AiConfig {

    public static final String STATE_ENABLED  = "1";
    public static final String STATE_DISABLED = "0";

    public enum DomainSignal { DEFAULT_CHANGED, UPDATED, DISABLED }

    // ── 标识 ─────────────────────────────────────────────────────────────────
    private Integer       configId;
    private Integer       userId;

    // ── 元数据 ────────────────────────────────────────────────────────────────
    private String configName;
    private String configDesc;
    private String configType;
    private String modelType;
    private String provider;

    // ── 凭证 ──────────────────────────────────────────────────────────────────
    private String appId;
    private String apiKey;
    private String apiSecret;
    private String ak;
    private String sk;
    private String apiUrl;

    // ── 能力 ──────────────────────────────────────────────────────────────────
    private Boolean enableThinking;

    // ── 状态 ──────────────────────────────────────────────────────────────────
    private String  state;
    private boolean isDefault;

    // ── 时间戳 ────────────────────────────────────────────────────────────────
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private final List<DomainSignal> signals = new ArrayList<>();

    /** 仅供 ConfigConverter 重建使用 */
    public AiConfig() {}

    // ── 工厂方法 ──────────────────────────────────────────────────────────────

    public static AiConfig newConfig(Integer userId, String configType, String provider,
                                     String configName, String configDesc, String modelType,
                                     String appId, String apiKey, String apiSecret,
                                     String ak, String sk, String apiUrl,
                                     Boolean enableThinking, boolean isDefault) {
        AiConfig c = new AiConfig();
        c.userId     = userId;
        c.configType = configType;
        c.provider   = provider;
        c.configName = configName;
        c.configDesc = configDesc;
        c.modelType  = modelType;
        c.appId      = appId;
        c.apiKey     = apiKey;
        c.apiSecret  = apiSecret;
        c.ak         = ak;
        c.sk         = sk;
        c.apiUrl     = apiUrl;
        c.enableThinking = enableThinking;
        c.state      = STATE_ENABLED;
        c.isDefault  = isDefault;
        if (isDefault) c.signals.add(DomainSignal.DEFAULT_CHANGED);
        return c;
    }

    public static AiConfig newConfig(Integer userId, ConfigBO bo) {
        return newConfig(userId, bo.getConfigType(), bo.getProvider(),
                bo.getConfigName(), bo.getConfigDesc(), bo.getModelType(),
                bo.getAppId(), bo.getApiKey(), bo.getApiSecret(),
                bo.getAk(), bo.getSk(), bo.getApiUrl(),
                bo.getEnableThinking(), "1".equals(bo.getIsDefault()));
    }

    /** 从持久层重建聚合根（Repository 专用，不产生任何信号）。 */
    public static AiConfig reconstitute(Integer configId, Integer userId,
                                        String configType, String provider,
                                        String configName, String configDesc, String modelType,
                                        String appId, String apiKey, String apiSecret,
                                        String ak, String sk, String apiUrl,
                                        Boolean enableThinking,
                                        String state, boolean isDefault,
                                        LocalDateTime createTime, LocalDateTime updateTime) {
        AiConfig c = new AiConfig();
        c.configId   = configId;
        c.userId     = userId;
        c.configType = configType;
        c.provider   = provider;
        c.configName = configName;
        c.configDesc = configDesc;
        c.modelType  = modelType;
        c.appId      = appId;
        c.apiKey     = apiKey;
        c.apiSecret  = apiSecret;
        c.ak         = ak;
        c.sk         = sk;
        c.apiUrl     = apiUrl;
        c.enableThinking = enableThinking;
        c.state      = state;
        c.isDefault  = isDefault;
        c.createTime = createTime;
        c.updateTime = updateTime;
        return c;
    }

    // ── 行为方法 ──────────────────────────────────────────────────────────────

    /** 将此配置设为默认（Repository.save 负责清除同类其他默认标记）。 */
    public void setAsDefault() {
        if (!this.isDefault) {
            this.isDefault = true;
            signals.add(DomainSignal.DEFAULT_CHANGED);
        }
    }

    /** 由 Repository 在 resetDefault 流程中调用，不产生信号。 */
    public void clearDefault() {
        this.isDefault = false;
    }

    public void update(ConfigBO bo) {
        update(bo.getConfigName(), bo.getConfigDesc(), bo.getModelType(), bo.getProvider(),
                bo.getAppId(), bo.getApiKey(), bo.getApiSecret(), bo.getAk(), bo.getSk(),
                bo.getApiUrl(), bo.getEnableThinking(),
                bo.getIsDefault() == null ? null : "1".equals(bo.getIsDefault()));
    }

    public void update(String configName, String configDesc, String modelType, String provider,
                       String appId, String apiKey, String apiSecret, String ak, String sk,
                       String apiUrl, Boolean enableThinking, Boolean isDefault) {
        if (configName != null) this.configName = configName;
        if (configDesc != null) this.configDesc = configDesc;
        if (modelType  != null) this.modelType  = modelType;
        if (provider   != null) this.provider   = provider;
        if (appId      != null) this.appId      = appId;
        if (apiKey     != null) this.apiKey     = apiKey;
        if (apiSecret  != null) this.apiSecret  = apiSecret;
        if (ak         != null) this.ak         = ak;
        if (sk         != null) this.sk         = sk;
        if (apiUrl     != null) this.apiUrl     = apiUrl;
        if (enableThinking != null) this.enableThinking = enableThinking;
        if (isDefault  != null) {
            if (isDefault && !this.isDefault) {
                this.isDefault = true;
                signals.add(DomainSignal.DEFAULT_CHANGED);
            } else if (!isDefault) {
                this.isDefault = false;
            }
        }
        signals.add(DomainSignal.UPDATED);
    }

    /** 软删除：禁用并取消默认。 */
    public void disable() {
        this.state     = STATE_DISABLED;
        this.isDefault = false;
        signals.add(DomainSignal.DISABLED);
    }

    /** insert 后由 Repository 回填自增主键，不产生信号。 */
    public void assignId(Integer configId) {
        this.configId = configId;
    }

    /** 取出并清空信号队列，供 Repository 发布领域事件。 */
    public List<DomainSignal> pullSignals() {
        List<DomainSignal> s = List.copyOf(signals);
        signals.clear();
        return s;
    }
}

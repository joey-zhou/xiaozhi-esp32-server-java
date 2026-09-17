package com.xiaozhi.config;

import com.xiaozhi.common.exception.ConfirmRequiredException;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigPageReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.common.model.resp.ConfigResp;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.storage.service.StorageReferenceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配置的读写编排：Req/Resp ↔ BO 转换、聚合根存取的事务边界，以及换对象存储前的存量校验。
 * <p>拿配置去 Provider 试拨不在这里，见 {@link ConfigConnectionChecker}。
 */
@Service
public class ConfigAppService {

    private static final String OSS_CONFIG_TYPE = "oss";

    @Resource
    private ConfigService configService;

    @Resource
    private ConfigConvert configConvert;

    @Resource
    private ConfigRepository configRepository;

    @Resource
    private StorageReferenceService storageReferenceService;

    public PageResult<ConfigResp> page(ConfigPageReq req, Integer userId) {
        ConfigPageReq r = req == null ? new ConfigPageReq() : req;
        return configService.page(r.getPageNo(), r.getPageSize(),
            r.getConfigType(), r.getConfigName(), r.getModelType(),
            r.getProvider(), r.getIsDefault(), r.getState(), userId)
            .map(configConvert::toResp);
    }

    @Transactional
    public ConfigResp create(ConfigCreateReq req, Integer userId, boolean storageSwitchConfirmed) {
        if (!storageSwitchConfirmed
                && OSS_CONFIG_TYPE.equals(req.getConfigType())
                && ConfigBO.DEFAULT_YES.equals(req.getIsDefault())) {
            checkStrandedReferences();
        }
        ConfigBO bo = configConvert.toBO(req);
        bo.setUserId(userId);
        AiConfig config = AiConfig.newConfig(userId, bo);
        configRepository.save(config);
        ConfigBO created = configService.getBO(config.getConfigId());
        return configConvert.toResp(created);
    }

    @Transactional
    public ConfigResp update(Integer configId, ConfigUpdateReq req, boolean storageSwitchConfirmed) {
        ConfigBO existing = configService.getBO(configId);
        if (existing == null) {
            throw new ResourceNotFoundException("配置不存在或无权访问");
        }
        checkStorageSwitch(existing, req, storageSwitchConfirmed);

        AiConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("配置不存在或无权访问"));
        config.update(configConvert.toBO(req));
        configRepository.save(config);
        ConfigBO updated = configService.getBO(configId);
        return configConvert.toResp(updated);
    }

    @Transactional
    public void delete(Integer configId, boolean storageSwitchConfirmed) {
        ConfigBO existing = configService.getBO(configId);
        if (existing == null) {
            throw new ResourceNotFoundException("配置不存在或无权访问");
        }
        // 删掉当前默认的那条 oss 配置，等于把存储切回本地，历史云地址一样解析不出来，
        // 与「默认让位」是同一件事，判定口径保持一致
        if (!storageSwitchConfirmed
                && OSS_CONFIG_TYPE.equals(existing.getConfigType())
                && ConfigBO.DEFAULT_YES.equals(existing.getIsDefault())) {
            checkStrandedReferences();
        }
        configRepository.delete(configId);
    }
    /**
     * 只有默认那条 oss 配置决定当前生效的存储，因此三种改动会让历史地址失效：
     * 非默认改成默认、默认让位（含改回本地）、默认自己换掉地址前缀。
     */
    private void checkStorageSwitch(ConfigBO existing, ConfigUpdateReq req, boolean storageSwitchConfirmed) {
        if (storageSwitchConfirmed || !OSS_CONFIG_TYPE.equals(existing.getConfigType())) {
            return;
        }
        boolean switched = ConfigBO.DEFAULT_YES.equals(existing.getIsDefault())
                ? ConfigBO.DEFAULT_NO.equals(req.getIsDefault()) || urlPrefixChanged(existing, req)
                : ConfigBO.DEFAULT_YES.equals(req.getIsDefault());
        if (switched) {
            checkStrandedReferences();
        }
    }

    /**
     * 判断改动是否会改掉地址前缀：provider 决定域名形态，configName 是桶名，appId 是地域，apiUrl 是 endpoint；
     * ak/sk 这类凭证不进前缀，改了不影响历史地址能不能解析。
     */
    private boolean urlPrefixChanged(ConfigBO existing, ConfigUpdateReq req) {
        return fieldChanged(req.getProvider(), existing.getProvider())
                || fieldChanged(req.getConfigName(), existing.getConfigName())
                || fieldChanged(req.getAppId(), existing.getAppId())
                || fieldChanged(req.getApiUrl(), existing.getApiUrl());
    }

    /** null 表示本次不改这个字段，与 {@link AiConfig#update} 的字段合并语义一致 */
    private boolean fieldChanged(String patchValue, String currentValue) {
        return patchValue != null && !patchValue.equals(currentValue);
    }

    /**
     * 存量统计要扫消息表，只在改动确实会换掉存储时才跑，别让每次改配置都付这笔开销。
     */
    private void checkStrandedReferences() {
        long count = storageReferenceService.countOnCurrentStorage();
        if (count > 0) {
            throw new ConfirmRequiredException(
                    "当前对象存储上还有 " + count + " 条历史音频/文件，切换后这些内容将永久无法访问");
        }
    }
}

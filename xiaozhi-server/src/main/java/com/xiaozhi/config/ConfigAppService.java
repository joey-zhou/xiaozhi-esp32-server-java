package com.xiaozhi.config;

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
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配置的读写编排：Req/Resp ↔ BO 转换、聚合根存取的事务边界，写前拦截规则见 {@link ConfigChangePolicy}。
 * <p>拿配置去 Provider 试拨不在这里，见 {@link ConfigTestAppService}。
 */
@Service
public class ConfigAppService {

    @Resource
    private ConfigService configService;

    @Resource
    private ConfigConvert configConvert;

    @Resource
    private ConfigRepository configRepository;

    @Resource
    private ConfigChangePolicy configChangePolicy;

    public PageResult<ConfigResp> page(ConfigPageReq req, Integer userId) {
        ConfigPageReq r = req == null ? new ConfigPageReq() : req;
        return configService.page(r.getPageNo(), r.getPageSize(),
            r.getConfigType(), r.getConfigName(), r.getModelType(),
            r.getProvider(), r.getIsDefault(), r.getState(), userId)
            .map(configConvert::toResp);
    }

    @Transactional
    public ConfigResp create(ConfigCreateReq req, Integer userId, boolean storageSwitchConfirmed) {
        configChangePolicy.checkCreate(req, storageSwitchConfirmed);
        ConfigBO bo = configConvert.toBO(req);
        bo.setUserId(userId);
        AiConfig config = AiConfig.newConfig(userId, bo);
        configRepository.save(config);
        return configConvert.toResp(config);
    }

    @Transactional
    public ConfigResp update(Integer configId, ConfigUpdateReq req, boolean storageSwitchConfirmed) {
        AiConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("配置不存在或无权访问"));
        // 拦截规则要的是改动前的值：空补丁合并出的就是聚合根当前字段的快照，不用再单独查一次配置
        configChangePolicy.checkUpdate(config.mergePatch(new ConfigBO()), req, storageSwitchConfirmed);
        config.update(configConvert.toBO(req));
        configRepository.save(config);
        return configConvert.toResp(config);
    }


    @Transactional
    public void delete(Integer configId, boolean storageSwitchConfirmed) {
        ConfigBO existing = configService.getBO(configId);
        if (existing == null) {
            throw new ResourceNotFoundException("配置不存在或无权访问");
        }
        configChangePolicy.checkDelete(existing, storageSwitchConfirmed);
        configRepository.delete(configId);
    }
}

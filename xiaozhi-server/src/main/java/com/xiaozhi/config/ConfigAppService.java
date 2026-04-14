package com.xiaozhi.config;

import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigPageReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.common.model.resp.ConfigResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import com.xiaozhi.config.service.ConfigService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配置领域应用服务。
 * <p>
 * 职责：编排 Controller → Domain Service 之间的流程，包括：
 * <ul>
 *   <li>Req/Resp ↔ BO 转换</li>
 *   <li>跨领域业务规则校验（embedding 模型绑定记忆检查）</li>
 *   <li>副作用协调（Redis 广播配置变更）</li>
 * </ul>
 * Controller 应只做参数绑定和权限校验，所有业务编排逻辑归此类。
 */
@Service
public class ConfigAppService {

    @Resource
    private ConfigService configService;

    @Resource
    private ConfigConvert configConvert;

    @Resource
    private ConfigRepository configRepository;

    public PageResp<ConfigResp> page(ConfigPageReq req, Integer userId) {
        ConfigPageReq r = req == null ? new ConfigPageReq() : req;
        return configService.page(r.getPageNo(), r.getPageSize(),
            r.getConfigType(), r.getConfigName(), r.getModelType(),
            r.getProvider(), r.getIsDefault(), r.getState(), userId);
    }

    @Transactional
    public ConfigResp create(ConfigCreateReq req, Integer userId) {
        ConfigBO bo = configConvert.toBO(req);
        bo.setUserId(userId);
        AiConfig config = AiConfig.newConfig(userId, bo);
        configRepository.save(config);
        ConfigBO created = configService.getBO(config.getConfigId());
        return configConvert.toResp(created);
    }

    @Transactional
    public ConfigResp update(Integer configId, ConfigUpdateReq req) {
        ConfigBO existing = configService.getBO(configId);
        if (existing == null) {
            throw new ResourceNotFoundException("配置不存在或无权访问");
        }
        AiConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("配置不存在或无权访问"));
        config.update(configConvert.toBO(req));
        configRepository.save(config);
        ConfigBO updated = configService.getBO(configId);
        return configConvert.toResp(updated);
    }

    @Transactional
    public void delete(Integer configId) {
        ConfigBO existing = configService.getBO(configId);
        if (existing == null) {
            throw new ResourceNotFoundException("配置不存在或无权访问");
        }
        configRepository.delete(configId);
    }

}

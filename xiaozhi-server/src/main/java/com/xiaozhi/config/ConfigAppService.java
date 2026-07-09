package com.xiaozhi.config;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigPageReq;
import com.xiaozhi.common.model.req.ConfigTestReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.common.model.resp.ConfigResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import com.xiaozhi.config.service.ConfigService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

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
@Slf4j
@Service
public class ConfigAppService {

    @Resource
    private ConfigService configService;

    @Resource
    private ConfigConvert configConvert;

    @Resource
    private ConfigRepository configRepository;

    @Resource
    private ChatModelFactory chatModelFactory;

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

    /**
     * 测试配置：使用表单当前值（可能未保存）直接发起一次真实调用，将 Provider 报错原样返回给前端。
     */
    public ApiResponse<Void> test(ConfigTestReq req) {
        if (!"llm".equals(req.getConfigType())) {
            return ApiResponse.error("暂不支持测试该类型配置");
        }
        ConfigBO bo = configConvert.toBO(req);
        if (bo.getConfigId() != null) {
            ConfigBO existing = configService.getBO(bo.getConfigId());
            if (existing != null) {
                if (!StringUtils.hasText(bo.getApiKey())) {
                    bo.setApiKey(existing.getApiKey());
                }
                if (!StringUtils.hasText(bo.getApiSecret())) {
                    bo.setApiSecret(existing.getApiSecret());
                }
                if (!StringUtils.hasText(bo.getAk())) {
                    bo.setAk(existing.getAk());
                }
                if (!StringUtils.hasText(bo.getSk())) {
                    bo.setSk(existing.getSk());
                }
            }
        }
        try {
            if (ConfigBO.ModelType.embedding.getValue().equals(bo.getModelType())) {
                var embeddingModel = chatModelFactory.getEmbeddingModel(bo);
                float[] vector = embeddingModel.embed("测试");
                return ApiResponse.success("连接成功，返回向量维度：" + vector.length);
            }
            ChatModel chatModel = chatModelFactory.createChatModel(bo, new RoleBO());
            ChatResponse response = chatModel.call(new Prompt(new UserMessage("你好，这是一次配置测试，请用一句话回复。")));
            String reply = response.getResult().getOutput().getText();
            return ApiResponse.success("连接成功，模型返回：" + reply);
        } catch (Exception e) {
            log.warn("配置测试失败 - provider: {}, model: {}", bo.getProvider(), bo.getConfigName(), e);
            return ApiResponse.error(extractErrorMessage(e));
        }
    }

    private String extractErrorMessage(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof RestClientResponseException rcre) {
                String body = rcre.getResponseBodyAsString();
                return StringUtils.hasText(body) ? body : rcre.getMessage();
            }
            t = t.getCause();
        }
        return StringUtils.hasText(e.getMessage()) ? e.getMessage() : "连接测试失败";
    }
}

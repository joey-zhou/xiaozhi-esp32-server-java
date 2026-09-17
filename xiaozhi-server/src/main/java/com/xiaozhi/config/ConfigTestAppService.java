package com.xiaozhi.config;

import com.xiaozhi.ai.probe.ConfigProbe;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.ConfigProbeResultBO;
import com.xiaozhi.common.model.req.ConfigTestReq;
import com.xiaozhi.config.convert.ConfigConvert;
import com.xiaozhi.config.domain.AiConfig;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 配置试拨的编排：把表单值与库里保存的配置合并成一份真正要外呼的配置，交给 ConfigProbe 去 Provider 试拨。
 * <p>真正拨号、翻译报错的逻辑在 xiaozhi-ai 的 {@link ConfigProbe}，这里只管定出「用哪份配置」。
 */
@Service
public class ConfigTestAppService {

    @Resource
    private ConfigConvert configConvert;

    @Resource
    private ConfigRepository configRepository;

    @Resource
    private ConfigProbe configProbe;

    /**
     * 测试配置：使用表单当前值（可能未保存）直接发起一次真实调用，把结果翻译成给前端看的结论。
     * <p>配置类型以表单提交的值为准，不查库判断——配置可能正在改、还没保存，用库里旧值判类型会文不对题。
     * <p>库里保存的密钥只能配库里保存的端点使用。本人的配置允许用表单值覆盖端点，
     * 共享配置一律按库里那条原样测试，表单里的端点与密钥都不生效。
     */
    public ConfigProbeResultBO test(ConfigTestReq req, Integer userId) {
        String configType = req.getConfigType();
        if (!"llm".equals(configType) && !"stt".equals(configType)) {
            return ConfigProbeResultBO.failure("暂不支持测试该类型配置");
        }
        ConfigBO form = configConvert.toBO(req).setUserId(userId);
        AiConfig saved = form.getConfigId() == null
                ? null
                : configRepository.findById(form.getConfigId()).orElse(null);
        ConfigBO bo = resolveConfigUnderTest(saved, form, userId);
        return configProbe.probe(bo);
    }

    /**
     * 定出本次真正拿去外呼的配置。
     * 未保存的表单整条用表单值；本人已保存的配置用表单值覆盖、缺的补库里；
     * 他人共享的配置整条用库里的，表单不参与，避免已保存的密钥被配上调用方指定的端点。
     */
    private ConfigBO resolveConfigUnderTest(AiConfig saved, ConfigBO form, Integer userId) {
        if (saved == null) {
            return form;
        }
        if (Objects.equals(saved.getUserId(), userId)) {
            return saved.mergePatch(form);
        }
        return saved.mergePatch(new ConfigBO());
    }
}

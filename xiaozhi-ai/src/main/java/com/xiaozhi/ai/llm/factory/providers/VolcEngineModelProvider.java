package com.xiaozhi.ai.llm.factory.providers;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 火山引擎（VolcEngine / ARK / 豆包）模型提供者。
 * <p>
 * 协议兼容 OpenAI，故复用 {@link OpenAiModelProvider} 的全部请求构建逻辑，
 * 覆盖火山专属的 Chat API 请求参数：
 * <ul>
 *     <li>火山通过 {@code reasoning_effort} 控制思考，<b>省略该参数时默认按 high 思考</b>；</li>
 *     <li>因此关闭思考必须显式下发 {@code reasoning_effort=minimal}，否则无法真正关闭。</li>
 *     <li>Chat API 固定下发 {@code service_tier=fast}，使用火山的优先推理服务。</li>
 * </ul>
 */
@Slf4j
@Component
public class VolcEngineModelProvider extends OpenAiModelProvider {

    @Override
    public String getProviderName() {
        return "volcengine";
    }

    @Override
    protected void applyThinkingOptions(OpenAiChatOptions.Builder builder, boolean enableThinking, String model) {
        if (enableThinking) {
            // 不下发 reasoning_effort，火山默认按 high 思考
            log.info("VolcEngine model {} 已启用思考模式，reasoningEffort=high(默认)", model);
        } else {
            // 火山省略参数会默认思考，必须显式 minimal 才能关闭
            builder.reasoningEffort("minimal");
            log.info("VolcEngine model {} 已关闭思考模式，reasoningEffort=minimal", model);
        }
    }

    @Override
    protected void applyProviderOptions(OpenAiChatOptions.Builder builder, String model) {
        builder.serviceTier("fast");
        log.info("VolcEngine model {} 已启用优先推理，serviceTier=fast", model);
    }
}

package com.xiaozhi.ai.llm.factory.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住火山引擎相对 OpenAI 协议的两处请求参数差异：Chat API 固定 service_tier=fast；
 * 关闭思考必须显式下发 reasoning_effort=minimal（省略该参数时火山默认按 high 思考，
 * 回归后只表现为 token 变多、首字变慢，不会报错）。
 */
class VolcEngineModelProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MODEL = "doubao-seed-1-6";

    private JsonNode chatApiRequest(OpenAiChatOptions.Builder builder) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsString(builder.build()));
    }

    @Test
    void appliesFastServiceTierToChatApiRequest() throws Exception {
        var builder = OpenAiChatOptions.builder().model(MODEL);

        new VolcEngineModelProvider().applyProviderOptions(builder, MODEL);

        assertThat(chatApiRequest(builder).path("service_tier").asText()).isEqualTo("fast");
    }

    @Test
    void thinkingDisabledSendsMinimalReasoningEffort() throws Exception {
        var builder = OpenAiChatOptions.builder().model(MODEL);

        new VolcEngineModelProvider().applyThinkingOptions(builder, false, MODEL);

        assertThat(chatApiRequest(builder).path("reasoning_effort").asText()).isEqualTo("minimal");
    }

    @Test
    void thinkingEnabledOmitsReasoningEffort() throws Exception {
        var builder = OpenAiChatOptions.builder().model(MODEL);

        new VolcEngineModelProvider().applyThinkingOptions(builder, true, MODEL);

        assertThat(chatApiRequest(builder).has("reasoning_effort")).isFalse();
    }
}

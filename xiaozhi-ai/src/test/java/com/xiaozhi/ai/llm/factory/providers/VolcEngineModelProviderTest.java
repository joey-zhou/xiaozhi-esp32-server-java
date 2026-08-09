package com.xiaozhi.ai.llm.factory.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VolcEngineModelProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void appliesFastServiceTierToChatApiRequest() throws Exception {
        var provider = new VolcEngineModelProvider();
        var builder = OpenAiChatOptions.builder().model("doubao-seed-1-6");

        provider.applyProviderOptions(builder, "doubao-seed-1-6");

        var requestJson = objectMapper.readTree(objectMapper.writeValueAsString(builder.build()));
        assertEquals("fast", requestJson.path("service_tier").asText());
    }
}

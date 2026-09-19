package com.xiaozhi.ai.llm;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.retry.NonTransientAiException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * JSON 模式按厂商下发：OpenAI 兼容用 response_format，Ollama 用 format；认不出的模型不带选项。
 * 兼容接口回 4xx 说明不认这个参数，退回普通调用再试一次，其它错误照常抛。
 */
class JsonModeTest {

    @Test
    void openAiModelsAskForAJsonObject() {
        ChatModel model = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().apiKey("test").build())
                .build();

        ChatOptions options = JsonMode.options(model);

        assertThat(options).isInstanceOf(OpenAiChatOptions.class);
        assertThat(((OpenAiChatOptions) options).getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    void ollamaModelsAskForJsonFormat() {
        ChatModel model = OllamaChatModel.builder().ollamaApi(OllamaApi.builder().build()).build();

        ChatOptions options = JsonMode.options(model);

        assertThat(options).isInstanceOf(OllamaChatOptions.class);
        assertThat(((OllamaChatOptions) options).getFormat()).isEqualTo("json");
    }

    @Test
    void unknownModelsAreCalledWithoutOptions() {
        List<ChatOptions> seen = new ArrayList<>();

        String result = JsonMode.call(mock(ChatModel.class), options -> {
            seen.add(options);
            return "{}";
        });

        assertThat(result).isEqualTo("{}");
        assertThat(seen).containsExactly((ChatOptions) null);
    }

    @Test
    void rejectedJsonModeFallsBackToAPlainCall() {
        ChatModel model = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().apiKey("test").build())
                .build();
        List<ChatOptions> seen = new ArrayList<>();

        String result = JsonMode.call(model, options -> {
            seen.add(options);
            if (options != null) {
                throw new NonTransientAiException("400 - response_format is not supported");
            }
            return "{}";
        });

        assertThat(result).isEqualTo("{}");
        assertThat(seen).hasSize(2);
        assertThat(seen.get(0)).isInstanceOf(OpenAiChatOptions.class);
        assertThat(seen.get(1)).isNull();
    }

    @Test
    void otherFailuresAreNotRetried() {
        ChatModel model = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().apiKey("test").build())
                .build();
        List<ChatOptions> seen = new ArrayList<>();

        assertThatThrownBy(() -> JsonMode.call(model, options -> {
            seen.add(options);
            throw new IllegalStateException("超时");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(seen).hasSize(1);
    }
}

package com.xiaozhi.ai.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;

import java.util.function.Function;

/**
 * 要模型只输出 JSON 的调用（如插话判定）按厂商开 JSON 模式，
 * 让服务端保证输出是合法 JSON，而不是靠事后修补。
 * <p>
 * 只认得出 OpenAI 兼容、Ollama、智谱三种模型，其它模型不带选项照常调用；提示词里必须出现 JSON 字样，
 * OpenAI 系接口对没提 JSON 的请求会直接拒绝。摘要是自然语言，不走这里。
 */
@Slf4j
public final class JsonMode {

    private JsonMode() {
    }

    /** 让这个模型只回 JSON 对象的运行时选项，认不出厂商时为 null */
    public static ChatOptions options(ChatModel chatModel) {
        if (chatModel instanceof OpenAiChatModel) {
            return OpenAiChatOptions.builder()
                    .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                    .build();
        }
        if (chatModel instanceof OllamaChatModel) {
            return OllamaChatOptions.builder().format("json").build();
        }
        if (chatModel instanceof ZhiPuAiChatModel) {
            return ZhiPuAiChatOptions.builder()
                    .responseFormat(ZhiPuAiApi.ChatCompletionRequest.ResponseFormat.jsonObject())
                    .build();
        }
        return null;
    }

    /**
     * 带 JSON 模式调用一次；兼容接口不认 response_format 会回 4xx，这时退回普通调用再试一次，
     * 输出交给 {@code LenientJson} 兜底。invoke 收到 null 表示不带选项。
     */
    public static String call(ChatModel chatModel, Function<ChatOptions, String> invoke) {
        ChatOptions options = options(chatModel);
        if (options == null) {
            return invoke.apply(null);
        }
        try {
            return invoke.apply(options);
        } catch (NonTransientAiException e) {
            log.warn("模型不接受 JSON 模式，退回普通调用: {}", e.getMessage());
            return invoke.apply(null);
        }
    }
}

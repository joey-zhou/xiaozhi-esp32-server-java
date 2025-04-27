package com.xiaozhi.websocket.llm.providers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaozhi.websocket.llm.api.AbstractLlmService;
import com.xiaozhi.websocket.llm.api.StreamResponseListener;
import okhttp3.*;
import okio.BufferedSource;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智谱 LLM服务实现
 */
public class ZhiPuService extends AbstractLlmService {

    /**
     * 构造函数
     *
     * @param endpoint API端点 (host url)
     * @param apiKey   API密钥 (apiKey)
     * @param model    模型名称
     */
    public ZhiPuService(String endpoint, String appId, String apiKey, String apiSecret, String model) {
        super(endpoint, appId, apiKey, apiSecret, model);
    }

    @Override
    protected String chat(List<Map<String, String>> messages) throws IOException {
        logger.warn("不支持，请使用流式对话");
        return StringUtils.EMPTY;
    }

    @Override
    protected void chatStream(List<Map<String, String>> messages, StreamResponseListener streamListener)
            throws IOException {
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<String, Object>() {{
                put("model", model);
                put("stream", true);
                put("messages", messages);
            }};

            // 转换为JSON
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            // 构建请求 - 使用简单的Bearer token认证
            Request request = new Request.Builder()
                    .url(endpoint + "/chat/completions")
                    .post(RequestBody.create(jsonBody, JSON))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiSecret) // 使用apiSecret作为Bearer token
                    .build();

            // 通知开始
            streamListener.onStart();

            // 发送请求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    logger.error("流式请求失败: {}", e.getMessage(), e);
                    streamListener.onError(e);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorMsg = "流式请求响应失败: " + response;
                        logger.error(errorMsg);
                        streamListener.onError(new IOException(errorMsg));
                        return;
                    }

                    try (ResponseBody responseBody = response.body()) {

                        BufferedSource source = responseBody.source();
                        StringBuilder fullResponse = new StringBuilder();

                        while (!source.exhausted()) {
                            String line = source.readUtf8Line();
                            if (line == null) {
                                break;
                            }
                            if (line.isEmpty() || line.equals("data: [DONE]")) {
                                continue;
                            }
                            if (line.startsWith("data:")) {
                                String jsonData = line.substring(6);

                                try {
                                    Map<String, Object> data = objectMapper.readValue(jsonData,
                                            new TypeReference<Map<String, Object>>() {
                                            });

                                    // 解析内容
                                    List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
                                    if (choices != null && !choices.isEmpty()) {
                                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");

                                        if (delta != null && delta.containsKey("content")) {
                                            String content = (String) delta.get("content");
                                            if (content != null && !content.isEmpty()) {
                                                streamListener.onToken(content);
                                                fullResponse.append(content);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.error("解析流式响应失败: {}", e.getMessage(), e);
                                    streamListener.onError(e);
                                }
                            }
                        }

                        // 通知完成
                        streamListener.onComplete(fullResponse.toString());
                    }
                }
            });
        } catch (Exception e) {
            throw new IOException("智谱API流式请求失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "zhipu";
    }
}
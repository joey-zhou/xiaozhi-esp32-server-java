package com.xiaozhi.dialogue.llm.providers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okio.BufferedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OpenAiLlmService {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected static final ObjectMapper objectMapper = new ObjectMapper();
    protected static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    protected final String endpoint;
    protected final String apiKey;
    protected final String model;
    /**
     * 构造函数
     *
     * @param endpoint  API端点
     * @param apiKey    API密钥
     * @param model     模型名称
     */
    public OpenAiLlmService(String endpoint, String apiKey, String model) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String chat(String sysMessage, String userMessage, boolean enableThinking) {
        long startTime = System.currentTimeMillis();
        logger.debug("LLM开始调用，使用的模型是：{}，深度思考参数：{}", getModel(), enableThinking);

        List<Map<String, Object>> formattedMessages = new ArrayList<>();
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", sysMessage);
        formattedMessages.add(systemMsg);

        Map<String, Object> currentUserMsg = new HashMap<>();
        currentUserMsg.put("role", "user");
        currentUserMsg.put("content", userMessage);
        formattedMessages.add(currentUserMsg);

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", formattedMessages);

        if(enableThinking){
            requestBody.put("stream", true);
            requestBody.put("enable_thinking", true);
        }
        StringBuilder fullResponse = new StringBuilder();

        try{
            // 转换为JSON
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // 构建请求
            Request request = new Request.Builder()
                    .url(endpoint + "/chat/completions")
                    .post(RequestBody.create(jsonBody, JSON))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            // 发送请求
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("请求失败: " + response);
                }
                if(enableThinking) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody == null) {
                            String errorMsg = "响应体为空";
                            logger.error(errorMsg);
                        }

                        BufferedSource source = responseBody.source();

                        while (!source.exhausted()) {
                            String line = source.readUtf8Line();
                            if (line == null) {
                                break;
                            }
                            if (line.isEmpty() || line.equals("data: [DONE]")) {
                                continue;
                            }
                            if (line.startsWith("data: ")) {
                                String jsonData = line.substring(6);
                                try {
                                    Map<String, Object> data = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {
                                    });
                                    List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
                                    if (choices != null && !choices.isEmpty()) {
                                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                                        if (delta != null) {
                                            //处理普通消息内容(有的ai，比如腾讯hunyuan-lite，工具调消息里还给了总结思考文本，这里就丢弃不要了)
                                            if (delta.containsKey("content")) {
                                                String content = (String) delta.get("content");
                                                if (content != null && !content.isEmpty()) {
                                                    fullResponse.append(content);
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.error("解析流式响应失败: {}", e.getMessage(), e);
                                }
                            }
                        }
                    }
                }else {
                    String responseBody = response.body().string();
                    Map<String, Object> responseMap = objectMapper.readValue(responseBody, new TypeReference<>() {});
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        if (message != null) {
                            return (String) message.get("content");
                        }
                    }
                }
            }
        }catch (Exception e){
            logger.error("调用{}模型发生错误", model, e);
        }
        logger.debug("LLM调用耗时: {} ms， 返回内容： {}", System.currentTimeMillis() - startTime, fullResponse);
        return !fullResponse.isEmpty() ? fullResponse.toString() : null;
    }

    /**
     * 流式对话方法，使用SSE实时返回每个token
     * 
     * @param sysMessage 系统消息
     * @param userMessage 用户消息
     * @param enableThinking 是否启用深度思考
     * @param tokenCallback 每个token的回调函数
     * @return 完整的响应内容
     */
    public String chatStream(String sysMessage, String userMessage, boolean enableThinking, TokenCallback tokenCallback) {
        long startTime = System.currentTimeMillis();
        logger.debug("LLM开始流式调用，使用的模型是：{}，深度思考参数：{}", getModel(), enableThinking);

        List<Map<String, Object>> formattedMessages = new ArrayList<>();
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", sysMessage);
        formattedMessages.add(systemMsg);

        Map<String, Object> currentUserMsg = new HashMap<>();
        currentUserMsg.put("role", "user");
        currentUserMsg.put("content", userMessage);
        formattedMessages.add(currentUserMsg);

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", formattedMessages);
        requestBody.put("stream", true); // 流式响应必须为true
        
        if(enableThinking){
            requestBody.put("enable_thinking", true);
        }
        
        StringBuilder fullResponse = new StringBuilder();

        try{
            // 转换为JSON
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // 构建请求
            Request request = new Request.Builder()
                    .url(endpoint + "/chat/completions")
                    .post(RequestBody.create(jsonBody, JSON))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            // 发送请求
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("请求失败: " + response);
                }
                
                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        String errorMsg = "响应体为空";
                        logger.error(errorMsg);
                        throw new IOException(errorMsg);
                    }

                    BufferedSource source = responseBody.source();

                    while (!source.exhausted()) {
                        String line = source.readUtf8Line();
                        if (line == null) {
                            break;
                        }
                        if (line.isEmpty() || line.equals("data: [DONE]")) {
                            continue;
                        }
                        if (line.startsWith("data: ")) {
                            String jsonData = line.substring(6);
                            try {
                                Map<String, Object> data = objectMapper.readValue(jsonData, new TypeReference<Map<String, Object>>() {});
                                List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
                                if (choices != null && !choices.isEmpty()) {
                                    Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                                    if (delta != null) {
                                        // 处理普通消息内容
                                        if (delta.containsKey("content")) {
                                            String content = (String) delta.get("content");
                                            if (content != null && !content.isEmpty()) {
                                                fullResponse.append(content);
                                                // 调用回调函数，实时返回每个token
                                                if (tokenCallback != null) {
                                                    tokenCallback.onToken(content);
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                logger.error("解析流式响应失败: {}", e.getMessage(), e);
                                // 解析失败时不再继续处理后续数据，避免继续调用可能已失效的回调
                                break;
                            }
                        }
                    }
                }
            }
        }catch (Exception e){
            logger.error("调用{}模型发生错误", model, e);
        }
        
        logger.debug("LLM流式调用耗时: {} ms， 返回内容： {}", System.currentTimeMillis() - startTime, fullResponse);
        return !fullResponse.isEmpty() ? fullResponse.toString() : null;
    }

    /**
     * Token回调接口，用于实时接收每个token
     */
    public interface TokenCallback {
        /**
         * 当接收到新的token时调用
         * @param token 新的token内容
         */
        void onToken(String token);
    }

    public boolean testFunctionCall(){
        List<Map<String, Object>> formattedMessages = new ArrayList<>();
        // 添加提示词信息
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一个工具助手，能够调用函数来完成任务。目前只有一个function: test_function");
        formattedMessages.add(systemMsg);

        // 添加当前用户消息
        Map<String, Object> currentUserMsg = new HashMap<>();
        currentUserMsg.put("role", "user");
        currentUserMsg.put("content", "帮我调用function: test_function");
        formattedMessages.add(currentUserMsg);

        Map<String , Object> functionCall = new HashMap<>();
        functionCall.put("type", "function");
        Map<String , Object> functionDef = new HashMap<>();
        functionDef.put("name", "test_function");
        functionDef.put("description", "这是函数test_function");
        functionCall.put("function", functionDef);

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", formattedMessages);
        requestBody.put("tools", List.of(functionCall));
        try{
            // 转换为JSON
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            // 构建请求
            Request request = new Request.Builder()
                    .url(endpoint + "/chat/completions")
                    .post(RequestBody.create(jsonBody, JSON))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();
            // 发送请求
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return false;
                }
                String responseBody = response.body().string();

                Map<String, Object> responseMap = objectMapper.readValue(responseBody,
                        new TypeReference<>() {});
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null) {
                        return message.containsKey("tool_calls");
                    }
                }
            }
        }catch (Exception e){
            logger.error("调用{}模型检查是否支持FunctionCall发生错误", model, e);
        }
        return false;
    }

    public String getModel() {
        return model;
    }

}

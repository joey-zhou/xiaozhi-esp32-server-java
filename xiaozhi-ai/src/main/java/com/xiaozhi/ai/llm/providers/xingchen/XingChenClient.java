package com.xiaozhi.ai.llm.providers.xingchen;

import com.xiaozhi.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 讯飞星辰Agent API 客户端实现
 * 基于文档: https://www.xfyun.cn/doc/spark/Agent04-API%E6%8E%A5%E5%85%A5.html
 * 
 * API地址: https://xingchen-api.xf-yun.com/workflow/v1/chat/completions
 * 认证方式: Bearer token (使用 APIKey:APISecret 格式)
 */
@Slf4j
public class XingChenClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    // API 端点
    private static final String API_BASE_URL = "https://xingchen-api.xf-yun.com";
    private static final String CHAT_COMPLETIONS_PATH = "/workflow/v1/chat/completions";
    private static final String RESUME_PATH = "/workflow/v1/resume";
    
    // 流式响应标识
    private static final String DATA_PREFIX = "data:";
    private static final String EVENT_PREFIX = "event:";
    
    private final String baseUrl;
    private final String flowId;
    private final String bearerToken; // APIKey:APISecret
    private final OkHttpClient httpClient;

    /**
     * 构造函数
     * @param apiKey API密钥
     * @param apiSecret API密钥
     * @param flowId 工作流ID
     */
    public XingChenClient(String baseUrl, String apiKey, String apiSecret, String flowId) {
        this.baseUrl = baseUrl != null && !baseUrl.isEmpty() ? baseUrl : API_BASE_URL;
        this.flowId = flowId;
        this.bearerToken = apiKey + ":" + apiSecret;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        log.info("XingChenClient初始化: baseUrl={}, flowId={}", this.baseUrl, flowId);
    }

    /**
     * 兼容旧构造函数
     */
    public XingChenClient(String endpoint, String apiKey, String apiSecret) {
        this(endpoint, apiKey, apiSecret, null);
    }

    public String getFlowId() {
        return flowId;
    }

    /**
     * 发送同步聊天消息
     */
    public XingChenResponse sendChatMessage(XingChenRequest request) throws IOException {
        log.debug("发送同步对话消息: flowId={}, uid={}", request.getFlowId(), request.getUid());
        
        // 确保非流式
        request.setStream(false);
        
        String jsonBody = JsonUtil.toJson(request);
        log.debug("请求体: {}", jsonBody);
        
        Request httpRequest = new Request.Builder()
                .url(baseUrl + CHAT_COMPLETIONS_PATH)
                .post(RequestBody.create(jsonBody, JSON))
                .addHeader("Authorization", "Bearer " + bearerToken)
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "无响应体";
                log.error("API请求失败: code={}, body={}", response.code(), errorBody);
                throw new IOException("API请求失败: " + response.code() + ", " + errorBody);
            }
            
            String responseBody = response.body().string();
            log.debug("响应: {}", responseBody);
            
            return JsonUtil.fromJson(responseBody, XingChenResponse.class);
        }
    }

    /**
     * 发送流式聊天消息
     */
    public void sendChatMessageStream(XingChenRequest request, XingChenChatStreamCallback callback) throws IOException {
        log.debug("发送流式对话消息: flowId={}, uid={}", request.getFlowId(), request.getUid());
        
        // 确保流式模式
        request.setStream(true);
        
        String jsonBody = JsonUtil.toJson(request);
        log.debug("请求体: {}", jsonBody);
        
        Request httpRequest = new Request.Builder()
                .url(baseUrl + CHAT_COMPLETIONS_PATH)
                .post(RequestBody.create(jsonBody, JSON))
                .addHeader("Authorization", "Bearer " + bearerToken)
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "无响应体";
                log.error("API请求失败: code={}, body={}", response.code(), errorBody);
                callback.onException(new IOException("API请求失败: " + response.code() + ", " + errorBody));
                return;
            }
            
            ResponseBody body = response.body();
            if (body == null) {
                callback.onException(new IOException("响应体为空"));
                return;
            }
            
            // 处理SSE流
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    
                    // 处理SSE事件
                    if (line.startsWith(EVENT_PREFIX)) {
                        String eventType = line.substring(EVENT_PREFIX.length()).trim();
                        log.debug("收到事件类型: {}", eventType);
                        continue;
                    }
                    
                    // 处理数据行
                    if (line.startsWith(DATA_PREFIX)) {
                        String jsonData = line.substring(DATA_PREFIX.length()).trim();
                        if (jsonData.isEmpty() || "[DONE]".equals(jsonData)) {
                            log.debug("流结束");
                            callback.onMessageEnd(null);
                            break;
                        }
                        
                        try {
                            XingChenResponse event = JsonUtil.fromJson(jsonData, XingChenResponse.class);
                            
                            // 检查是否有错误
                            if (event.getCode() != null && event.getCode() != 0) {
                                log.error("API返回错误: code={}, message={}", event.getCode(), event.getMessage());
                                callback.onError(event);
                                continue;
                            }
                            
                            // 检查是否是工具调用事件
                            if (event.getEventData() != null) {
                                log.debug("收到工具调用事件: {}", JsonUtil.toJson(event.getEventData()));
                                callback.onFunctionCall(event);
                            } else if (event.getChoices() != null && !event.getChoices().isEmpty()) {
                                // 普通消息事件
                                XingChenResponse.Choices choice = event.getChoices().get(0);
                                if ("stop".equals(choice.getFinishReason())) {
                                    log.debug("消息结束");
                                    callback.onMessageEnd(event);
                                    break;
                                } else {
                                    callback.onMessage(event);
                                }
                            }
                        } catch (Exception e) {
                            log.error("解析响应数据失败: {}", jsonData, e);
                            callback.onException(e);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("流式请求失败", e);
            callback.onException(e);
        }
    }

    /**
     * 发送Resume请求(用于工具调用后继续对话)
     */
    public void resume(XingChenResume resume, XingChenChatStreamCallback callback) throws IOException {
        log.debug("发送Resume请求: {}", JsonUtil.toJson(resume));
        
        String jsonBody = JsonUtil.toJson(resume);
        
        Request httpRequest = new Request.Builder()
                .url(baseUrl + RESUME_PATH)
                .post(RequestBody.create(jsonBody, JSON))
                .addHeader("Authorization", "Bearer " + bearerToken)
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "无响应体";
                log.error("Resume请求失败: code={}, body={}", response.code(), errorBody);
                callback.onException(new IOException("Resume请求失败: " + response.code() + ", " + errorBody));
                return;
            }
            
            ResponseBody body = response.body();
            if (body == null) {
                callback.onException(new IOException("响应体为空"));
                return;
            }
            
            // 处理SSE流
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    
                    // 处理数据行
                    if (line.startsWith(DATA_PREFIX)) {
                        String jsonData = line.substring(DATA_PREFIX.length()).trim();
                        if (jsonData.isEmpty() || "[DONE]".equals(jsonData)) {
                            log.debug("Resume流结束");
                            callback.onMessageEnd(null);
                            break;
                        }
                        
                        try {
                            XingChenResponse event = JsonUtil.fromJson(jsonData, XingChenResponse.class);
                            
                            // 检查是否有错误
                            if (event.getCode() != null && event.getCode() != 0) {
                                log.error("Resume返回错误: code={}, message={}", event.getCode(), event.getMessage());
                                callback.onError(event);
                                continue;
                            }
                            
                            // 检查结束标志
                            if (event.getChoices() != null && !event.getChoices().isEmpty()) {
                                XingChenResponse.Choices choice = event.getChoices().get(0);
                                if ("stop".equals(choice.getFinishReason())) {
                                    log.debug("Resume消息结束");
                                    callback.onMessageEnd(event);
                                    break;
                                }
                            }
                            
                            callback.onMessage(event);
                        } catch (Exception e) {
                            log.error("解析Resume响应失败: {}", jsonData, e);
                            callback.onException(e);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Resume请求失败", e);
            callback.onException(e);
        }
    }
}

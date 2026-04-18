package com.xiaozhi.ai.llm.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
/**
 * 讯飞星火大模型实现
 * 支持的模型版本:
 * - X1 (x1): 深度推理模型,对标OpenAI o1和DeepSeek R1,支持推理、数学、代码等任务
 * - 4.0Ultra (generalv4): 最强大的星火大模型,32K输入/32K输出
 * - Max (generalv3.5): 旗舰级大语言模型,8K输入/8K输出
 * - Max-32K (generalv3.5-32k): Max的32K版本
 * - Pro (generalv3): 专业级大语言模型,8K输入/8K输出
 * - Pro-128K (generalv3-128k): Pro的128K版本,128K输入/4K输出
 * - Lite (general): 轻量级大语言模型,8K输入/4K输出,免费使用
 * 
 * API文档: 
 * - V1模型: https://www.xfyun.cn/doc/spark/HTTP%E8%B0%83%E7%94%A8%E6%96%87%E6%A1%A3.html
 * - X1模型: https://www.xfyun.cn/doc/spark/X1http.html
 */
@Slf4j
public class XingHuoChatModel implements ChatModel {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    // 讯飞星火API地址
    private static final String SPARK_V1_API_URL = "https://spark-api-open.xf-yun.com/v1/chat/completions";
    private static final String SPARK_X1_API_URL = "https://spark-api-open.xf-yun.com/v2/chat/completions";
    
    private final OkHttpClient httpClient;
    private final String apiPassword;
    private final String model;
    private final String baseUrl;

    /**
     * 构造函数
     * 
     * @param apiPassword API密码,从控制台获取
     * @param model 模型名称,如: generalv4, generalv3.5, generalv3, general
     */
    public XingHuoChatModel(String apiPassword, String model) {
        this.apiPassword = apiPassword;
        this.model = model;
        // 根据模型选择API地址: X1模型使用v2接口,其他使用v1接口
        this.baseUrl = "x1".equalsIgnoreCase(model) ? SPARK_X1_API_URL : SPARK_V1_API_URL;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
    }

    public String getProviderName() {
        return "xinghuo";
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        
        try {
            // 构建请求体
            Map<String, Object> requestBody = buildRequestBody(prompt, false);
            
            // 发送请求
            Request request = buildRequest(requestBody);
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("星火API请求失败: code={}, message={}", response.code(), response.message());
                    return ChatResponse.builder().generations(Collections.emptyList()).build();
                }
                
                String responseBody = response.body().string();
                
                Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
                
                // 解析响应
                
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null) {
                        String content = (String) message.get("content");
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("model", model);
                        if (responseMap.containsKey("usage")) {
                            metadata.put("usage", responseMap.get("usage"));
                        }
                        
                        return new ChatResponse(
                                List.of(new Generation(AssistantMessage.builder().content(content).properties(metadata).build()))
                        );
                    }
                }
                
                return ChatResponse.builder().generations(Collections.emptyList()).build();
                
            }
        } catch (Exception e) {
            log.error("星火大模型调用失败", e);
            return ChatResponse.builder().generations(Collections.emptyList()).build();
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.create(sink -> {
            log.debug("星火大模型流式调用: model={}", model);
            
            // 使用数组来存储标志(因为在匿名内部类中需要修改)
            final boolean[] hasToolCall = {false};
            
            // 累积工具调用信息 - 星火流式返回会分多次发送
            final Map<String, Map<String, Object>> toolCallsAccumulator = new HashMap<>();
            
            try {
                // 构建请求体
                Map<String, Object> requestBody = buildRequestBody(prompt, true);
                
                // 发送流式请求
                Request request = buildRequest(requestBody);
                
                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        log.error("星火API流式请求失败", e);
                        sink.error(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            String errorBody = response.body() != null ? response.body().string() : "无响应体";
                            sink.error(new IOException("星火API请求失败: " + response.code() + " " + response.message() + ", 详情: " + errorBody));
                            return;
                        }
                        
                        try (ResponseBody responseBody = response.body()) {
                            if (responseBody == null) {
                                sink.error(new IOException("响应体为空"));
                                return;
                            }
                            
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
                                
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.isEmpty() || line.equals("data: [DONE]")) {
                                        continue;
                                    }
                                    
                                    if (line.startsWith("data: ")) {
                                        String jsonData = line.substring(6);
                                        processStreamLine(jsonData, sink, prompt, hasToolCall, toolCallsAccumulator);
                                    }
                                }
                                
                                // 流读取完成后的处理
                                if (!hasToolCall[0]) {
                                    // 没有工具调用,正常结束
                                    sink.complete();
                                } else if (!toolCallsAccumulator.isEmpty()) {
                                    // 验证累积的工具调用是否完整
                                    boolean hasValidToolCall = toolCallsAccumulator.values().stream().anyMatch(tool -> {
                                        
                                        Map<String, Object> func = (Map<String, Object>) tool.get("function");
                                        if (func == null) return false;
                                        String name = (String) func.get("name");
                                        return name != null && !name.isEmpty();
                                    });
                                    
                                    if (hasValidToolCall) {
                                        // 有工具调用且name不为空,执行工具调用
                                        List<Map<String, Object>> finalToolCalls = new ArrayList<>(toolCallsAccumulator.values());
                                        processToolCalls(finalToolCalls, sink, prompt);
                                        sink.complete();
                                    } else {
                                        // name为空,说明工具调用不完整,发送错误提示并结束
                                        String errorMessage = "抱歉,工具调用失败了。请重新描述您的需求。";
                                        AssistantMessage assistantMessage = new AssistantMessage(errorMessage);
                                        ChatResponse errorResponse = new ChatResponse(
                                                List.of(new Generation(assistantMessage))
                                        );
                                        sink.next(errorResponse);
                                        sink.complete();
                                    }
                                } else {
                                    // 有hasToolCall标记但accumulator为空,异常情况
                                    String errorMessage = "抱歉,工具调用失败了。请重新描述您的需求。";
                                    AssistantMessage assistantMessage = new AssistantMessage(errorMessage);
                                    ChatResponse errorResponse = new ChatResponse(
                                            List.of(new Generation(assistantMessage))
                                    );
                                    sink.next(errorResponse);
                                    sink.complete();
                                }
                            }
                        } catch (Exception e) {
                            log.error("处理流式响应失败", e);
                            sink.error(e);
                        }
                    }
                });
                
            } catch (Exception e) {
                log.error("星火大模型流式调用失败", e);
                sink.error(e);
            }
        });
    }

    /**
     * 处理流式响应的每一行
     */
    private void processStreamLine(String jsonData, reactor.core.publisher.FluxSink<ChatResponse> sink, 
                                   Prompt prompt, boolean[] hasToolCall,
                                   Map<String, Map<String, Object>> toolCallsAccumulator) {
        try {
            // 添加原始JSON日志
            
            
            Map<String, Object> data = objectMapper.readValue(jsonData, Map.class);
            
            List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
            
            if (choices == null || choices.isEmpty()) {
                return;
            }
            
            Map<String, Object> choice = choices.get(0);
            
            Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
            
            if (delta == null) {
                return;
            }
            
            // 处理普通内容
            if (delta.containsKey("content")) {
                String content = (String) delta.get("content");
                if (content != null && !content.isEmpty()) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("model", model);
                    
                    sink.next(ChatResponse.builder()
                            .generations(List.of(new Generation(
                                    AssistantMessage.builder().content(content).properties(metadata).build())))
                            .build());
                }
            }
            
            // 处理工具调用 - 累积参数,不立即执行
            if (delta.containsKey("tool_calls")) {
                hasToolCall[0] = true;
                
                Object toolCallsObj = delta.get("tool_calls");
                String toolCallsJson = objectMapper.writeValueAsString(toolCallsObj);
                
                // 累积工具调用信息
                accumulateToolCalls(toolCallsObj, toolCallsAccumulator);
            }
            
            // 检查是否结束 - 流结束时才执行工具调用
            String finishReason = (String) choice.get("finish_reason");
            if (finishReason != null && !finishReason.isEmpty()) {
                
                // 有工具调用时检查是否完整
                if (hasToolCall[0] && !toolCallsAccumulator.isEmpty()) {
                    // 验证工具调用是否完整(有name且有arguments)
                    // 注意: arguments可以是空字符串"",但必须存在且不是null
                    boolean allToolsComplete = toolCallsAccumulator.values().stream().allMatch(tool -> {
                        
                        Map<String, Object> func = (Map<String, Object>) tool.get("function");
                        if (func == null) return false;
                        String name = (String) func.get("name");
                        String args = (String) func.get("arguments");
                        // name必须不为空,arguments必须存在(可以是"")
                        boolean hasName = name != null && !name.isEmpty();
                        boolean hasArgs = args != null;
                        boolean complete = hasName && hasArgs;
                        return complete;
                    });
                    
                    if (allToolsComplete) {
                        List<Map<String, Object>> finalToolCalls = new ArrayList<>(toolCallsAccumulator.values());
                        processToolCalls(finalToolCalls, sink, prompt);
                        sink.complete();
                    }
                } else if (!hasToolCall[0]) {
                    // 没有工具调用,正常结束
                    sink.complete();
                }
            }
            
        } catch (Exception e) {
            log.error("❌ 解析流式响应失败: {}", jsonData, e);
        }
    }
    
    /**
     * 累积工具调用信息 - 星火流式返回会分多次发送tool_calls
     * 策略: 使用index作为key(如果有),否则使用"tool_0"作为默认key
     */
    private void accumulateToolCalls(Object toolCallsObj, Map<String, Map<String, Object>> accumulator) {
        try {
            List<Map<String, Object>> toolCallsList = new ArrayList<>();
            if (toolCallsObj instanceof List) {
                
                List<Map<String, Object>> list = (List<Map<String, Object>>) toolCallsObj;
                toolCallsList = list;
            } else if (toolCallsObj instanceof Map) {
                
                Map<String, Object> single = (Map<String, Object>) toolCallsObj;
                toolCallsList.add(single);
            }
            
            for (int i = 0; i < toolCallsList.size(); i++) {
                Map<String, Object> toolCall = toolCallsList.get(i);
                Object functionObj = toolCall.get("function");
                
                if (functionObj instanceof Map) {
                    
                    Map<String, Object> function = (Map<String, Object>) functionObj;
                    String name = (String) function.get("name");
                    
                    // 使用name作为key,如果name为空使用索引
                    String key = (name != null && !name.isEmpty()) ? name : "tool_" + i;
                    
                    // 获取或创建累积对象
                    Map<String, Object> existing = accumulator.getOrDefault(key, new HashMap<>());
                    
                    // 合并type
                    String type = (String) toolCall.get("type");
                    if (type != null && !type.isEmpty()) {
                        existing.put("type", type);
                    }
                    
                    // 获取或创建function对象
                    
                    Map<String, Object> existingFunc = (Map<String, Object>) existing.getOrDefault("function", new HashMap<>());
                    
                    // 累积name - 可能第一次为空,后续才有值
                    if (name != null && !name.isEmpty()) {
                        existingFunc.put("name", name);
                    }
                    
                    // 累积description
                    String description = (String) function.get("description");
                    if (description != null && !description.isEmpty()) {
                        existingFunc.put("description", description);
                    }
                    
                    // 累积arguments - 可能分多次返回
                    Object argsObj = function.get("arguments");
                    if (argsObj != null) {
                        String currentArgs = (argsObj instanceof String) ? (String) argsObj : "";
                        String existingArgs = (String) existingFunc.getOrDefault("arguments", "");
                        existingFunc.put("arguments", existingArgs + currentArgs);
                    }
                    
                    existing.put("function", existingFunc);
                    accumulator.put(key, existing);
                }
            }
        } catch (Exception e) {
            log.error("❌ 累积工具调用失败", e);
        }
    }

    /**
     * 处理工具调用
     */
    private void processToolCalls(List<Map<String, Object>> toolCalls, 
                                  reactor.core.publisher.FluxSink<ChatResponse> sink,
                                  Prompt prompt) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        
        try {
            List<AssistantMessage.ToolCall> assistantToolCalls = new ArrayList<>();
            
            for (int i = 0; i < toolCalls.size(); i++) {
                Map<String, Object> toolCall = toolCalls.get(i);
                
                // 提取字段 - 使用JSON重新解析确保类型正确
                String type = (String) toolCall.get("type");
                Object functionObj = toolCall.get("function");
                
                if (functionObj != null) {
                    // 统一转换为Map
                    
                    Map<String, Object> function = (functionObj instanceof Map) 
                        ? (Map<String, Object>) functionObj
                        : objectMapper.readValue(objectMapper.writeValueAsString(functionObj), Map.class);
                    
                    String name = (String) function.get("name");
                    Object argsObj = function.get("arguments");
                    // arguments可能是字符串或对象
                    String arguments = (argsObj instanceof String) 
                        ? (String) argsObj 
                        : objectMapper.writeValueAsString(argsObj);
                    
                    // 如果arguments为空字符串,转为空对象(表示无参数)
                    if (arguments == null || arguments.trim().isEmpty()) {
                        arguments = "{}";
                    }
                    
                    // 生成唯一ID
                    String id = (String) toolCall.getOrDefault("id", "tool_" + System.currentTimeMillis() + "_" + i);
                    
                    assistantToolCalls.add(new AssistantMessage.ToolCall(
                            id,
                            type != null ? type : "function",
                            name,
                            arguments
                    ));
                }
            }
            
            if (!assistantToolCalls.isEmpty()) {
                AssistantMessage assistantMessage = AssistantMessage.builder()
                        .content("")
                        .properties(Map.of("model", model))
                        .toolCalls(assistantToolCalls)
                        .build();
                        
                ChatResponse chatResponse = ChatResponse.builder()
                        .generations(List.of(new Generation(assistantMessage)))
                        .build();
                
                // 执行工具调用
                var toolExecutionResult = ToolCallingManager.builder().build()
                        .executeToolCalls(prompt, chatResponse);
                
                if (toolExecutionResult.returnDirect()) {
                    // 直接返回工具执行结果
                    sink.next(ChatResponse.builder().from(chatResponse)
                            .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                            .build());
                    sink.complete();
                } else {
                    // 工具执行结果需要发回模型继续对话
                    // 星火大模型暂不支持工具调用后继续对话,直接返回结果
                    sink.next(ChatResponse.builder().from(chatResponse)
                            .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                            .build());
                    sink.complete();
                }
            }
        } catch (Exception e) {
            log.error("❌ 处理工具调用失败", e);
            sink.error(e);
        }
    }

    /**
     * 构建请求体
     */
    private Map<String, Object> buildRequestBody(Prompt prompt, boolean stream) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", stream);
        
        // 转换消息格式 - 简化版本,只处理用户消息
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 添加系统消息(如果有)
        if (prompt.getInstructions().size() > 1) {
            // 第一条可能是系统消息
            Message firstMsg = prompt.getInstructions().get(0);
            if ("system".equals(firstMsg.getMessageType().getValue())) {
                Map<String, String> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", firstMsg.getText());
                messages.add(systemMsg);
            }
        }
        
        // 添加用户消息
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt.getUserMessage().getText());
        messages.add(userMsg);
        
        requestBody.put("messages", messages);
        
        // 添加工具定义(如果有) - 星火Max和Ultra支持Function Call
        ToolCallingChatOptions chatOptions = (ToolCallingChatOptions) prompt.getOptions();
        if (chatOptions != null && chatOptions.getToolCallbacks() != null && !chatOptions.getToolCallbacks().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            chatOptions.getToolCallbacks().forEach(toolCallback -> {
                try {
                    // 获取工具定义
                    var toolDefinition = toolCallback.getToolDefinition();
                    if (toolDefinition != null) {
                        // 将ToolDefinition转为JSON字符串,再解析为Map
                        String toolJson = com.xiaozhi.utils.JsonUtil.toJson(toolDefinition);
                        
                        Map<String, Object> toolMap = com.xiaozhi.utils.JsonUtil.fromJson(toolJson, Map.class);
                        
                        if (toolMap != null) {
                            // 构建符合讯飞星火格式的工具定义
                            // 格式: {"type":"function", "function":{"name": "...", "description": "...", "parameters": {...}}}
                            Map<String, Object> xinghuoTool = new HashMap<>();
                            xinghuoTool.put("type", "function");
                            
                            Map<String, Object> function = new HashMap<>();
                            function.put("name", toolMap.get("name"));
                            function.put("description", toolMap.get("description"));
                            
                            // 处理 parameters: inputSchema可能是字符串或对象,必须转换为对象!
                            Object inputSchema = toolMap.get("inputSchema");
                            Map<String, Object> parameters = null;
                            
                            if (inputSchema instanceof String) {
                                // 字符串转对象
                                String schemaStr = (String) inputSchema;
                                if (!schemaStr.isEmpty()) {
                                    try {
                                        parameters = objectMapper.readValue(schemaStr, Map.class);
                                    } catch (Exception e) {
                                        log.warn("解析inputSchema失败: {}", schemaStr, e);
                                    }
                                }
                            } else if (inputSchema instanceof Map) {
                                // 已经是对象
                                
                                Map<String, Object> map = (Map<String, Object>) inputSchema;
                                parameters = map;
                            }
                            
                            // 确保parameters是对象而不是字符串
                            if (parameters != null) {
                                function.put("parameters", parameters);
                            } else {
                                // 使用空对象作为默认值
                                function.put("parameters", Map.of("type", "object", "properties", Map.of()));
                            }
                            
                            xinghuoTool.put("function", function);
                            tools.add(xinghuoTool);
                        }
                    }
                } catch (Exception e) {
                    log.warn("添加工具定义失败: {}", e.getMessage(), e);
                }
            });
            
            if (!tools.isEmpty()) {
                requestBody.put("tools", tools);
            }
        }
        
        return requestBody;
    }

    /**
     * 构建HTTP请求
     */
    private Request buildRequest(Map<String, Object> requestBody) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        return new Request.Builder()
                .url(baseUrl)
                .post(RequestBody.create(jsonBody, JSON))
                .addHeader("Authorization", "Bearer " + apiPassword)
                .addHeader("Content-Type", "application/json")
                .build();
    }
}


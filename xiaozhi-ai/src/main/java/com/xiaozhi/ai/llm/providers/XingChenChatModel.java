package com.xiaozhi.ai.llm.providers;

import com.xiaozhi.ai.llm.providers.xingchen.*;
import com.xiaozhi.utils.JsonUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XingChenChatModel implements ChatModel {

    private XingChenClient chatClient;
    private final ToolCallingManager toolCallingManager;

    /**
     * 构造函数
     *
     * @param bearerToken 控制台配置的授权码(APIKey:APISecret)，直接作为 Bearer token 使用
     * @param flowId 工作流ID
     * @param toolCallingManager 应用统一装配的工具调用管理器，保证走本仓的工具链记录、事件与观测
     */
    public XingChenChatModel(String endpoint, String bearerToken, String flowId, ToolCallingManager toolCallingManager) {
        chatClient = new XingChenClient(endpoint, bearerToken, flowId);
        this.toolCallingManager = toolCallingManager;
    }

    public String getProviderName() {
        return "xingchen";
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ToolCallingChatOptions chatOptions = (ToolCallingChatOptions) prompt.getOptions();
        Map<String, Object> input = null;
        if (chatOptions != null) {
            input = Map.of(
                    "AGENT_USER_INPUT", prompt.getUserMessage().getText(),
                    "func_call", chatOptions.getToolCallbacks()
            );
            log.debug("工具数量: {}", chatOptions.getToolCallbacks().size());
        } else {
            input = Map.of(
                    "AGENT_USER_INPUT", prompt.getUserMessage().getText(),
                    "func_call", new ArrayList<>()
            );
        }
        // 创建聊天消息
        XingChenRequest message = XingChenRequest.builder()
                .flowId(chatClient.getFlowId())
                .uid(resolveUid(prompt))
                .parameters(
                        input
                )
                .ext(XingChenRequest.Ext.builder().botId("1").caller("workflow").build())
                .stream(false)
                .history(new ArrayList<>())
                .chatId(resolveChatId(prompt))
                .build();
        try {
            // 发送消息并获取响应
            XingChenResponse response = chatClient.sendChatMessage(message);
            // 安全检查: 确保 choices 不为空,与 stream() 保持一致
            if (response.getChoices() == null || response.getChoices().isEmpty()
                    || response.getChoices().get(0).getDelta() == null) {
                log.warn("收到空的 choices/delta,跳过此消息");
                return ChatResponse.builder().generations(Collections.emptyList()).build();
            }
            return new ChatResponse(List.of(new Generation(
                    AssistantMessage.builder()
                            .content(response.getChoices().get(0).getDelta().getContent())
                            .properties(Map.of("messageId", response.getId()))
                            .build()
            )));

        } catch (IOException e) {
            log.error("错误: ", e);
            return ChatResponse.builder().generations(Collections.emptyList()).build();
        }

    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Flux<ChatResponse> responseFlux = Flux.create(sink -> {

            ToolCallingChatOptions chatOptions = (ToolCallingChatOptions) prompt.getOptions();
            // 创建聊天消息
            XingChenRequest message = XingChenRequest.builder()
                    .flowId(chatClient.getFlowId())
                    .uid(resolveUid(prompt))
                    .parameters(
                            Map.of(
                                    "AGENT_USER_INPUT", prompt.getUserMessage().getText(),
                                    "func_call", chatOptions.getToolCallbacks()
                            )
                    )
                    .ext(XingChenRequest.Ext.builder().botId("1").caller("workflow").build())
                    .stream(true)
                    .history(new ArrayList<>())
                    .chatId(resolveChatId(prompt))
                    .build();

            // 使用数组来存储标志(因为在匿名内部类中需要修改)
            final boolean[] hasToolCall = {false};
            
            // 发送流式消息
            try {
                // 用户打断时取消上游请求，不让已经不需要的响应继续占用星辰的连接和算力配额
                chatClient.sendChatMessageStream(message, new XingChenChatStreamCallback() {
                    @Override
                    public void onMessage(XingChenResponse event) {
                        // 安全检查: 确保 choices 不为空
                        if (event.getChoices() == null || event.getChoices().isEmpty()) {
                            log.warn("收到空的 choices,跳过此消息");
                            return;
                        }
                        
                        XingChenResponse.Choices choice = event.getChoices().get(0);
                        if (choice.getDelta() == null) {
                            log.warn("收到空的 delta,跳过此消息");
                            return;
                        }
                        
                        String content = choice.getDelta().getContent();
                        if (content != null && !content.isEmpty()) {
                            sink.next(ChatResponse.builder()
                                    .generations(
                                            List.of(new Generation(AssistantMessage.builder()
                                                    .content(content)
                                                    .properties(Map.of("messageId", event.getId()))
                                                    .build())))
                                    .build());
                        }
                    }

                    @Override
                    public void onMessageEnd(XingChenResponse event) {
                        // 如果没有触发工具调用,这里就是真正的结束点
                        if (!hasToolCall[0]) {
                            log.debug("初始流结束且无工具调用,完成流程");
                            sink.complete();
                        } else {
                            log.debug("初始流结束但有工具调用,等待 resume 完成");
                        }
                    }

                    @Override
                    public void onFunctionCall(XingChenResponse event) {
                        // 标记有工具调用
                        hasToolCall[0] = true;
                        log.debug("触发工具调用");
                        
                        // 安全检查
                        if (event.getEventData() == null || event.getEventData().getValue() == null) {
                            log.error("EventData 或 Value 为空,无法执行工具调用");
                            sink.error(new IllegalStateException("无效的工具调用数据"));
                            return;
                        }
                        
                        XingChenResponse.EventData eventData = event.getEventData();
                        String content = eventData.getValue().getContent();
                        if (content == null || content.isEmpty()) {
                            log.error("工具调用内容为空");
                            sink.error(new IllegalStateException("工具调用内容为空"));
                            return;
                        }
                        
                        content = content.replace("```json", "").replace("```", "").trim();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = JsonUtil.fromJson(content, Map.class);
                        
                        if (map == null || !map.containsKey("name")) {
                            log.error("工具调用解析失败,无法获取工具名称: {}", content);
                            sink.error(new IllegalStateException("工具调用格式错误"));
                            return;
                        }
                        
                        List<AssistantMessage.ToolCall> toolCalls = List.of(
                                new AssistantMessage.ToolCall(
                                        "1",
                                        "function",
                                        (String) map.get("name"),
                                        JsonUtil.toJson(map.get("arguments")))
                        );
                        
                        // 获取消息内容(可能为空)
                        String messageContent = "";
                        if (event.getChoices() != null && !event.getChoices().isEmpty() 
                                && event.getChoices().get(0).getDelta() != null) {
                            messageContent = event.getChoices().get(0).getDelta().getContent();
                            if (messageContent == null) {
                                messageContent = "";
                            }
                        }

                        AssistantMessage assistantMessage = AssistantMessage.builder()
                                .content(messageContent)
                                .properties(Map.of("messageId", event.getId()))
                                .toolCalls(toolCalls)
                                .build();

                        Generation generation = new Generation(assistantMessage);
                        ChatResponse chatResponse = ChatResponse.builder()
                                .generations(List.of(generation))
                                .build();

                        var toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
                        
                        if (toolExecutionResult.returnDirect()) {
                            // Return tool execution result directly to the client.
                            sink.next(ChatResponse.builder().from(chatResponse)
                                    .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                                    .build());
                            // 如果直接返回,需要完成流
                            sink.complete();
                        } else {
                            // Send the tool execution result back to the model.
                            XingChenResume resume = XingChenResume.builder()
                                    .eventId(eventData.getEventId())
                                    .eventType("resume")
                                    .content("操作成功")
                                    .build();
                            // 将sink传递给resume方法,让resume的响应也能发送给客户端
                            resume(resume, sink);
                        }
                    }

                    @Override
                    public void onError(XingChenResponse event) {
                        sink.error(new IOException(event.toString()));
                    }

                    @Override
                    public void onException(Throwable throwable) {
                        log.error("异常: {}", throwable.getMessage());
                        sink.error(throwable);
                    }
                }, call -> sink.onCancel(call::cancel));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return responseFlux;
    }

    public void resume(XingChenResume resume, reactor.core.publisher.FluxSink<ChatResponse> sink) {
        try {
            log.debug("XingChen resume消息: {}", JsonUtil.toJson(resume));
            chatClient.resume(resume, new XingChenChatStreamCallback() {
                @Override
                public void onMessage(XingChenResponse event) {
                    // 安全检查: 确保 choices 不为空
                    if (event.getChoices() == null || event.getChoices().isEmpty()) {
                        log.warn("Resume 收到空的 choices,跳过此消息");
                        return;
                    }
                    
                    XingChenResponse.Choices choice = event.getChoices().get(0);
                    if (choice.getDelta() == null) {
                        log.warn("Resume 收到空的 delta,跳过此消息");
                        return;
                    }
                    
                    String content = choice.getDelta().getContent();
                    if (content != null && !content.isEmpty()) {
                        // 将resume的响应也发送给客户端
                        sink.next(ChatResponse.builder().generations(
                                        List.of(new Generation(AssistantMessage.builder()
                                                .content(content)
                                                .properties(Map.of("messageId", event.getId()))
                                                .build())))
                                .build());
                    }
                }

                @Override
                public void onMessageEnd(XingChenResponse event) {
                    // [DONE] 结束时 event 为 null
                    log.debug("Resume onMessageEnd,流程完成: messageId={}", event != null ? event.getId() : null);
                    // Resume流程结束,通知完成
                    sink.complete();
                }

                @Override
                public void onFunctionCall(XingChenResponse event) {
                    log.warn("Resume过程中又触发了FunctionCall,这可能不是预期行为: {}", JsonUtil.toJson(event));
                    // 如果 resume 后又触发了工具调用,需要递归处理
                    // 但这种情况比较特殊,暂时只记录警告
                }

                @Override
                public void onError(XingChenResponse event) {
                    log.error("Resume错误: code={}, message={}", event.getCode(), event.getMessage());
                    sink.error(new IOException("Resume错误: " + event.getMessage()));
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("Resume异常: {}", throwable.getMessage());
                    sink.error(throwable);
                }
            }, call -> sink.onCancel(call::cancel));
        } catch (IOException e) {
            log.error("发送resume请求失败", e);
            sink.error(e);
        } catch (Exception e) {
            log.error("Resume过程发生未预期异常", e);
            sink.error(e);
        }
    }

    /**
     * 从Prompt的ChatOptions中提取设备ID，生成确定性的用户ID。
     * 如果无法提取设备ID，则回退到基于UUID的用户ID。
     */
    private String resolveUid(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
            Map<String, Object> toolContext = toolCallingChatOptions.getToolContext();
            if (toolContext != null) {
                Object deviceIdObj = toolContext.get("deviceId");
                if (deviceIdObj instanceof String deviceId && !deviceId.isBlank()) {
                    return "user_xz_" + deviceId.replace(":", "");
                }
            }
        }
        return "user_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 从 ToolContext 取出 sessionId 作为星辰的 chat_id；拿不到时返回 null，星辰将其视为开启新会话。
     */
    private String resolveChatId(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
            Map<String, Object> toolContext = toolCallingChatOptions.getToolContext();
            if (toolContext != null) {
                Object sessionIdObj = toolContext.get("sessionId");
                if (sessionIdObj instanceof String sessionId && !sessionId.isBlank()) {
                    return sessionId;
                }
            }
        }
        return null;
    }
}
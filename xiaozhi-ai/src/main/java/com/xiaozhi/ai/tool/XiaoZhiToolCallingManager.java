package com.xiaozhi.ai.tool;

import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.ai.tool.session.ToolSessionProvider;
import com.xiaozhi.event.ToolCallCompletedEvent;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.observation.DefaultToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationDocumentation;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.HashMap;

/**
 * 自定义的工具调用管理器，用于处理工具调用和执行。
 * 基于Spring AI的DefaultToolCallingManager，增加了自定义的监控和元数据处理功能。
 * 包含对流式工具调用分片合并的修复（Spring AI issue #4629, #4790）。
 * 后续关注 Spring AI 是否有修复改问题
 */
public class XiaoZhiToolCallingManager implements ToolCallingManager, ApplicationContextAware {

    private static ApplicationContext applicationContext;

    private static final Logger logger = LoggerFactory.getLogger(XiaoZhiToolCallingManager.class);

    // @formatter:off

    private static final ObservationRegistry DEFAULT_OBSERVATION_REGISTRY
            = ObservationRegistry.NOOP;

    private static final ToolCallingObservationConvention DEFAULT_OBSERVATION_CONVENTION
            = new DefaultToolCallingObservationConvention();

    private static final ToolCallbackResolver DEFAULT_TOOL_CALLBACK_RESOLVER
            = new DelegatingToolCallbackResolver(List.of());

    private static final ToolExecutionExceptionProcessor DEFAULT_TOOL_EXECUTION_EXCEPTION_PROCESSOR
            = DefaultToolExecutionExceptionProcessor.builder().build();

    // @formatter:on
    private final ObservationRegistry observationRegistry;

    private final ToolCallbackResolver toolCallbackResolver;

    private final ToolExecutionExceptionProcessor toolExecutionExceptionProcessor;

    private ToolCallingObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

    public XiaoZhiToolCallingManager(ObservationRegistry observationRegistry, ToolCallbackResolver toolCallbackResolver,
                                     ToolExecutionExceptionProcessor toolExecutionExceptionProcessor) {
        Assert.notNull(observationRegistry, "observationRegistry cannot be null");
        Assert.notNull(toolCallbackResolver, "toolCallbackResolver cannot be null");
        Assert.notNull(toolExecutionExceptionProcessor, "toolCallExceptionConverter cannot be null");

        this.observationRegistry = observationRegistry;
        this.toolCallbackResolver = toolCallbackResolver;
        this.toolExecutionExceptionProcessor = toolExecutionExceptionProcessor;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        XiaoZhiToolCallingManager.applicationContext = applicationContext;
    }

    /**
     * 获取 ToolSessionProvider
     */
    private static ToolSessionProvider getToolSessionProvider() {
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(ToolSessionProvider.class);
            } catch (Exception e) {
                logger.debug("无法获取ToolSessionProvider: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 判断是否为MCP调用（根据工具名称判断，MCP工具通常以mcp_开头）
     */
    private static boolean isMcpCall(String toolName) {
        return toolName != null && toolName.startsWith("mcp_");
    }

    /**
     * 发布工具调用事件
     */
    private static void publishToolCallCompletedEvent(String sessionId, String toolName, String arguments,
                                          String result, boolean success, long startTimeMs) {
        if (applicationContext == null) {
            return;
        }
        try {
            long durationMs = startTimeMs > 0 ? System.currentTimeMillis() - startTimeMs : 0;
            applicationContext.publishEvent(new ToolCallCompletedEvent(
                    XiaoZhiToolCallingManager.class, sessionId, toolName, arguments, result, success, durationMs));
        } catch (Exception e) {
            logger.debug("发布工具调用事件失败: {}", e.getMessage());
        }
    }


    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        Assert.notNull(chatOptions, "chatOptions cannot be null");

        List<ToolCallback> toolCallbacks = new ArrayList<>(chatOptions.getToolCallbacks());
        for (String toolName : chatOptions.getToolNames()) {
            // Skip the tool if it is already present in the request toolCallbacks.
            // That might happen if a tool is defined in the options
            // both as a ToolCallback and as a tool name.
            if (chatOptions.getToolCallbacks()
                    .stream()
                    .anyMatch(tool -> tool.getToolDefinition().name().equals(toolName))) {
                continue;
            }
            ToolCallback toolCallback = this.toolCallbackResolver.resolve(toolName);
            if (toolCallback == null) {
                throw new IllegalStateException("No ToolCallback found for tool name: " + toolName);
            }
            toolCallbacks.add(toolCallback);
        }

        return toolCallbacks.stream().map(ToolCallback::getToolDefinition).toList();
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        Assert.notNull(prompt, "prompt cannot be null");
        Assert.notNull(chatResponse, "chatResponse cannot be null");

        Optional<Generation> toolCallGeneration = chatResponse.getResults()
                .stream()
                .filter(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
                .findFirst();

        if (toolCallGeneration.isEmpty()) {
            throw new IllegalStateException("No tool call requested by the chat model");
        }

        AssistantMessage originalAssistantMessage = toolCallGeneration.get().getOutput();

        // 修复流式分片导致的 ToolCall 拆分问题
        List<AssistantMessage.ToolCall> mergedToolCalls = mergeFragmentedToolCalls(originalAssistantMessage.getToolCalls());
        AssistantMessage assistantMessage = (mergedToolCalls == originalAssistantMessage.getToolCalls())
                ? originalAssistantMessage
                : AssistantMessage.builder()
                    .content(originalAssistantMessage.getText())
                    .properties(originalAssistantMessage.getMetadata())
                    .toolCalls(mergedToolCalls)
                    .build();

        ToolContext toolContext = buildToolContext(prompt, assistantMessage);

        XiaoZhiToolCallingManager.InternalToolExecutionResult internalToolExecutionResult = executeToolCall(prompt, assistantMessage,
                toolContext);

        // 将中间消息（模型的 tool_call 请求 + 工具执行结果）存入 ToolSession，供 Persona 注入 Conversation
        String sessionId = toolContext.getContext().get("sessionId") instanceof String s ? s : null;
        if (sessionId != null) {
            ToolSessionProvider provider = getToolSessionProvider();
            if (provider != null) {
                ToolSession toolSession = provider.getSession(sessionId);
                if (toolSession != null) {
                    toolSession.addToolCallMessages(assistantMessage, internalToolExecutionResult.toolResponseMessage());
                }
            }
        }

        List<Message> conversationHistory = buildConversationHistoryAfterToolExecution(prompt.getInstructions(),
                assistantMessage, internalToolExecutionResult.toolResponseMessage());

        return ToolExecutionResult.builder()
                .conversationHistory(conversationHistory)
                .returnDirect(internalToolExecutionResult.returnDirect())
                .build();
    }

    private static ToolContext buildToolContext(Prompt prompt, AssistantMessage assistantMessage) {
        Map<String, Object> toolContextMap = Map.of();

        if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions
                && !CollectionUtils.isEmpty(toolCallingChatOptions.getToolContext())) {
            toolContextMap = new HashMap<>(toolCallingChatOptions.getToolContext());

            List<Message> messageHistory = new ArrayList<>(prompt.copy().getInstructions());

            // 确保工具调用消息包含正确的元数据
            if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                Map<String, Object> metadata = new HashMap<>(assistantMessage.getMetadata());
                String toolName = assistantMessage.getToolCalls().get(0).name();
                metadata.put("toolName", toolName);
                AssistantMessage updatedAssistantMessage = AssistantMessage.builder()
                        .content(assistantMessage.getText())
                        .properties(metadata)
                        .toolCalls(assistantMessage.getToolCalls())
                        .build();
                messageHistory.add(updatedAssistantMessage);
            } else {
                messageHistory.add(AssistantMessage.builder()
                        .content(assistantMessage.getText())
                        .properties(assistantMessage.getMetadata())
                        .toolCalls(assistantMessage.getToolCalls())
                        .build());
            }

            toolContextMap.put(ToolContext.TOOL_CALL_HISTORY,
                    buildConversationHistoryBeforeToolExecution(prompt, assistantMessage));
        }

        return new ToolContext(toolContextMap);
    }

    private static List<Message> buildConversationHistoryBeforeToolExecution(Prompt prompt,
                                                                             AssistantMessage assistantMessage) {
        List<Message> messageHistory = new ArrayList<>(prompt.copy().getInstructions());

        // 确保工具调用消息包含正确的元数据
        if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
            Map<String, Object> metadata = new HashMap<>(assistantMessage.getMetadata());
            String toolName = assistantMessage.getToolCalls().get(0).name();
            metadata.put("toolName", toolName);
            AssistantMessage updatedAssistantMessage = AssistantMessage.builder()
                    .content(assistantMessage.getText())
                    .properties(metadata)
                    .toolCalls(assistantMessage.getToolCalls())
                    .build();
            messageHistory.add(updatedAssistantMessage);
        } else {
            messageHistory.add(AssistantMessage.builder()
                    .content(assistantMessage.getText())
                    .properties(assistantMessage.getMetadata())
                    .toolCalls(assistantMessage.getToolCalls())
                    .build());
        }

        return messageHistory;
    }

    /**
     * 修复流式响应中工具调用分片问题（Spring AI issue #4629, #4790）。
     * <p>
     * 部分 OpenAI 兼容 API（千问、阿里云等）在流式返回 tool call 时，续传 chunk 的 id 为空字符串 "" 而非 null，
     * 导致 OpenAiStreamFunctionCallingHelper.merge() 将同一个 tool call 的 name 和 arguments 拆成多条记录。
     * <p>
     * 已观测到的分片模式（同一 id 被拆成两条）：
     * <pre>
     *   分片[0]: id='call_xxx', name='get_device_status', arguments=''
     *   分片[1]: id='call_xxx', name='',                  arguments='{}'
     * </pre>
     * <p>
     * 合并策略：
     * - 相同 id 的条目属于同一个 tool call，合并 name 和 arguments
     * - id 为空且 name 为空的条目视为续传片段，合并到紧邻的上一个 tool call
     * - 合并后仍缺少 name 的条目会被 warn 并跳过（arguments 允许为空，部分工具不需要参数）
     */
    private static List<AssistantMessage.ToolCall> mergeFragmentedToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.size() <= 1) {
            return toolCalls;
        }

        // 快速检查：如果所有条目都有 name，说明没有分片问题，直接返回
        boolean hasFragment = toolCalls.stream()
                .anyMatch(tc -> !hasText(tc.name()));
        if (!hasFragment) {
            return toolCalls;
        }

        List<AssistantMessage.ToolCall> merged = new ArrayList<>();
        String currentId = null;
        String currentType = null;
        String currentName = null;
        StringBuilder currentArgs = null;

        for (AssistantMessage.ToolCall tc : toolCalls) {
            // 判断是否为续传：同一 id 或无 id 无 name 的孤立片段
            boolean isContinuation = currentId != null
                    && ((!hasText(tc.id()) && !hasText(tc.name()))
                        || (hasText(tc.id()) && tc.id().equals(currentId)));

            if (isContinuation) {
                // 续传片段：合并到当前 tool call
                if (hasText(tc.name()) && !hasText(currentName)) {
                    currentName = tc.name();
                }
                if (tc.arguments() != null && !tc.arguments().isEmpty()) {
                    currentArgs.append(tc.arguments());
                }
            } else {
                // 新的 tool call：先输出上一个
                if (currentName != null) {
                    merged.add(new AssistantMessage.ToolCall(currentId, currentType, currentName, currentArgs.toString()));
                }
                currentId = hasText(tc.id()) ? tc.id() : "";
                currentType = hasText(tc.type()) ? tc.type() : "function";
                currentName = hasText(tc.name()) ? tc.name() : null;
                currentArgs = new StringBuilder(tc.arguments() != null ? tc.arguments() : "");
            }
        }
        // 输出最后一个
        if (currentName != null) {
            merged.add(new AssistantMessage.ToolCall(currentId, currentType, currentName, currentArgs.toString()));
        }

        // 验证：只拦截缺 name 的，arguments 允许为空（部分工具不需要参数）
        List<AssistantMessage.ToolCall> valid = new ArrayList<>();
        for (AssistantMessage.ToolCall tc : merged) {
            if (!hasText(tc.name())) {
                logger.warn("工具调用合并后仍缺少 name，跳过: id={}, arguments={}", tc.id(), tc.arguments());
            } else {
                valid.add(tc);
            }
        }

        if (valid.size() != toolCalls.size()) {
            logger.info("工具调用分片合并完成: {} 条 → {} 条", toolCalls.size(), valid.size());
        }
        return valid;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Execute the tool call and return the response message.
     */
    private XiaoZhiToolCallingManager.InternalToolExecutionResult executeToolCall(Prompt prompt, AssistantMessage assistantMessage,
                                                                                  ToolContext toolContext) {
        List<ToolCallback> toolCallbacks = List.of();
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
            toolCallbacks = toolCallingChatOptions.getToolCallbacks();
        }

        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

        Boolean returnDirect = null;

        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

            String toolName = toolCall.name();
            String toolInputArguments = toolCall.arguments();

            ToolCallback toolCallback = toolCallbacks.stream()
                    .filter(tool -> toolName.equals(tool.getToolDefinition().name()))
                    .findFirst()
                    .orElseGet(() -> this.toolCallbackResolver.resolve(toolName));

            if (toolCallback == null) {
                // 模型幻觉调用了未注册的工具，返回错误结果让模型自行总结回复，而不是崩掉整个流
                logger.error("模型调用了未注册的工具: {}", toolName);
                toolResponses.add(new ToolResponseMessage.ToolResponse(
                        toolCall.id(), toolName,
                        "工具 '" + toolName + "' 不存在或未注册，请告知用户该功能当前不可用。"));
                continue;
            }

            if (returnDirect == null) {
                returnDirect = toolCallback.getToolMetadata().returnDirect();
            }
            else {
                returnDirect = returnDirect && toolCallback.getToolMetadata().returnDirect();
            }

            ToolCallingObservationContext observationContext = ToolCallingObservationContext.builder()
                    .toolDefinition(toolCallback.getToolDefinition())
                    .toolMetadata(toolCallback.getToolMetadata())
                    .toolCallArguments(toolInputArguments)
                    .build();
            // 通过 sessionId 获取 ToolSession（Persona 只传 sessionId 避免序列化问题）
            String sessionId = toolContext.getContext().get("sessionId") instanceof String s ? s : null;
            ToolSession toolSession = null;
            if (sessionId != null) {
                toolSession = getToolSessionProvider() != null ? getToolSessionProvider().getSession(sessionId) : null;
                observationContext.put("sessionId", sessionId);
            }

            // 记录工具调用开始时间
            final boolean isMcp = isMcpCall(toolName);
            final long[] startTimeRef = new long[]{System.currentTimeMillis()};
            final boolean[] successRef = new boolean[]{true};

            String toolCallResult = ToolCallingObservationDocumentation.TOOL_CALL
                    .observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
                            this.observationRegistry)
                    .observe(() -> {
                        String toolResult;
                        boolean success = true;
                        try {
                            toolResult = toolCallback.call(toolInputArguments, toolContext);
                        }
                        catch (ToolExecutionException ex) {
                            logger.error("Tool execution exception: ", ex);
                            toolResult = this.toolExecutionExceptionProcessor.process(ex);
                            logger.debug("Processed tool execution exception result: {}", toolResult);
                            success = false;
                            successRef[0] = false;
                        }
                        catch (Exception ex) {
                            logger.error("Unexpected exception during tool execution: ", ex);
                            toolResult = "Error executing tool: " + ex.getMessage();
                            success = false;
                            successRef[0] = false;
                        }
                        observationContext.setToolCallResult(toolResult);

                        return toolResult;
                    });

            // 记录工具调用详情到session
            if (toolSession != null) {
                toolSession.addToolCallDetail(toolName, toolInputArguments, toolCallResult);
            }

            // 发布工具调用事件
            publishToolCallCompletedEvent(sessionId, toolName, toolInputArguments, toolCallResult,
                    successRef[0], startTimeRef[0]);

            toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolName,
                    toolCallResult != null ? toolCallResult : ""));
        }

        return new XiaoZhiToolCallingManager.InternalToolExecutionResult(ToolResponseMessage.builder().responses(toolResponses).build(),
                returnDirect != null && returnDirect);
    }

    private List<Message> buildConversationHistoryAfterToolExecution(List<Message> previousMessages,
                                                                     AssistantMessage assistantMessage, ToolResponseMessage toolResponseMessage) {
        List<Message> messages = new ArrayList<>(previousMessages);

        // 确保工具调用消息包含正确的元数据
        if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
            Map<String, Object> metadata = new HashMap<>(assistantMessage.getMetadata());
            String toolName = assistantMessage.getToolCalls().get(0).name();
            metadata.put("toolName", toolName);
            AssistantMessage updatedAssistantMessage = AssistantMessage.builder()
                    .content(assistantMessage.getText())
                    .properties(metadata)
                    .toolCalls(assistantMessage.getToolCalls())
                    .build();
            messages.add(updatedAssistantMessage);
        } else {
            messages.add(assistantMessage);
        }

        messages.add(toolResponseMessage);
        return messages;
    }

    public void setObservationConvention(ToolCallingObservationConvention observationConvention) {
        this.observationConvention = observationConvention;
    }

    public static XiaoZhiToolCallingManager.Builder builder() {
        return new XiaoZhiToolCallingManager.Builder();
    }

    private record InternalToolExecutionResult(ToolResponseMessage toolResponseMessage, boolean returnDirect) {
    }

    public final static class Builder {

        private ObservationRegistry observationRegistry = DEFAULT_OBSERVATION_REGISTRY;

        private ToolCallbackResolver toolCallbackResolver = DEFAULT_TOOL_CALLBACK_RESOLVER;

        private ToolExecutionExceptionProcessor toolExecutionExceptionProcessor = DEFAULT_TOOL_EXECUTION_EXCEPTION_PROCESSOR;

        private Builder() {
        }

        public XiaoZhiToolCallingManager.Builder observationRegistry(ObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
            return this;
        }

        public XiaoZhiToolCallingManager.Builder toolCallbackResolver(ToolCallbackResolver toolCallbackResolver) {
            this.toolCallbackResolver = toolCallbackResolver;
            return this;
        }

        public XiaoZhiToolCallingManager.Builder toolExecutionExceptionProcessor(
                ToolExecutionExceptionProcessor toolExecutionExceptionProcessor) {
            this.toolExecutionExceptionProcessor = toolExecutionExceptionProcessor;
            return this;
        }

        public XiaoZhiToolCallingManager build() {
            return new XiaoZhiToolCallingManager(this.observationRegistry, this.toolCallbackResolver,
                    this.toolExecutionExceptionProcessor);
        }

    }
}

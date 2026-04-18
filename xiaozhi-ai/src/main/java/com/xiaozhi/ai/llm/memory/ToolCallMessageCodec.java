package com.xiaozhi.ai.llm.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MessageBO#toolCalls 字段的 JSON 编解码器。
 *
 * <p>作为写侧（DialogueTurnConverter）和读侧（{@link DatabaseChatMemory}）共享的
 * 格式契约：JSON 字段名在这里集中声明，任何一侧改动都会同步影响另一侧。
 *
 * <p>约定的两种负载：
 * <ul>
 *   <li>Tool call 请求（{@code sender=assistant, messageType=TOOL_CALL}）：
 *       {@code [{id, name, arguments}, ...]}</li>
 *   <li>Tool response 回执（{@code sender=tool, messageType=TOOL_RESPONSE}）：
 *       {@code [{toolCallId, toolName}, ...]}，响应文本本身存在 MessageBO#message 中。</li>
 * </ul>
 */
public final class ToolCallMessageCodec {

    /** Tool call 字段。*/
    static final String FIELD_ID = "id";
    static final String FIELD_NAME = "name";
    static final String FIELD_ARGUMENTS = "arguments";

    /** Tool response 字段。*/
    static final String FIELD_TOOL_CALL_ID = "toolCallId";
    static final String FIELD_TOOL_NAME = "toolName";

    /** Spring AI {@link AssistantMessage.ToolCall#type()} 目前固定为 "function"。 */
    private static final String TOOL_CALL_TYPE_FUNCTION = "function";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, String>>> RAW_LIST_TYPE = new TypeReference<>() {};

    private ToolCallMessageCodec() {}

    /** 序列化 AssistantMessage 的 toolCalls 列表。 */
    public static String encodeToolCalls(List<AssistantMessage.ToolCall> toolCalls) throws JsonProcessingException {
        List<Map<String, String>> raw = toolCalls.stream()
                .map(tc -> Map.of(
                        FIELD_ID, tc.id(),
                        FIELD_NAME, tc.name(),
                        FIELD_ARGUMENTS, tc.arguments()))
                .toList();
        return OBJECT_MAPPER.writeValueAsString(raw);
    }

    /** 反序列化为 AssistantMessage.ToolCall 列表；空/空白返回空列表。 */
    public static List<AssistantMessage.ToolCall> decodeToolCalls(String json) throws IOException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<Map<String, String>> raw = OBJECT_MAPPER.readValue(json, RAW_LIST_TYPE);
        return raw.stream()
                .map(m -> new AssistantMessage.ToolCall(
                        m.getOrDefault(FIELD_ID, ""),
                        TOOL_CALL_TYPE_FUNCTION,
                        m.getOrDefault(FIELD_NAME, ""),
                        m.getOrDefault(FIELD_ARGUMENTS, "")))
                .toList();
    }

    /** 序列化 ToolResponseMessage 的 responses（只保存 id / name，响应文本由调用方单独持久化）。 */
    public static String encodeToolResponses(List<ToolResponseMessage.ToolResponse> responses) throws JsonProcessingException {
        List<Map<String, String>> raw = responses.stream()
                .map(r -> Map.of(
                        FIELD_TOOL_CALL_ID, r.id(),
                        FIELD_TOOL_NAME, r.name()))
                .toList();
        return OBJECT_MAPPER.writeValueAsString(raw);
    }

    /**
     * 反序列化为 ToolResponseMessage.ToolResponse 列表。
     * <p>由于持久化时多条响应的文本被合并为 MessageBO#message 单条，反序列化时所有响应共用同一份 {@code content}。
     * <p>若 {@code json} 为空或解析出的列表为空，会返回一个兜底的单条响应（id、name 为空字符串）。
     */
    public static List<ToolResponseMessage.ToolResponse> decodeToolResponses(String json, String content) throws IOException {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        if (json != null && !json.isBlank()) {
            List<Map<String, String>> raw = OBJECT_MAPPER.readValue(json, RAW_LIST_TYPE);
            for (Map<String, String> m : raw) {
                responses.add(new ToolResponseMessage.ToolResponse(
                        m.getOrDefault(FIELD_TOOL_CALL_ID, ""),
                        m.getOrDefault(FIELD_TOOL_NAME, ""),
                        content));
            }
        }
        if (responses.isEmpty()) {
            responses.add(new ToolResponseMessage.ToolResponse("", "", content));
        }
        return responses;
    }
}

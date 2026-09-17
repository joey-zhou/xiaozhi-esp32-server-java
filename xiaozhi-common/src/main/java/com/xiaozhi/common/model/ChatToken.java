package com.xiaozhi.common.model;

/**
 * LLM 流式输出的 Token 单元，区分思考过程和正式回复。
 * <p>
 * 设备对话管道中，Synthesizer 只消费 {@code content} 类型的 Token，思考内容被过滤。
 * Web 聊天场景下，前端可同时接收 {@code thinking} 和 {@code content}，展示推理过程；
 * 模型调用失败时以一条 {@code error} 结束流，文本是失败原因。
 *
 * @param type 类型：{@code "thinking"} 表示推理/思考过程，{@code "content"} 表示正式回复，{@code "error"} 表示调用失败
 * @param text 文本内容
 */
public record ChatToken(String type, String text) {

    public static final String TYPE_THINKING = "thinking";
    public static final String TYPE_CONTENT = "content";
    public static final String TYPE_ERROR = "error";

    public static ChatToken thinking(String text) {
        return new ChatToken(TYPE_THINKING, text);
    }

    public static ChatToken content(String text) {
        return new ChatToken(TYPE_CONTENT, text);
    }

    public static ChatToken error(String reason) {
        return new ChatToken(TYPE_ERROR, reason);
    }

    public boolean isThinking() {
        return TYPE_THINKING.equals(type);
    }

    public boolean isContent() {
        return TYPE_CONTENT.equals(type);
    }
}

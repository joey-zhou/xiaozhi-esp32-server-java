package com.xiaozhi.ai.llm.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

/**
 * 对话组切分。一组从一条 UserMessage 起到下一条 UserMessage 之前，中间的 tool_call 与
 * ToolResponseMessage 同属一组。
 * <p>
 * 历史裁剪与批量摘要都必须整组进出：切在组中间会留下带 tool_calls 却没有响应的
 * assistant 消息，或没有对应 tool_call 的孤儿 tool 消息，下一次请求会被 OpenAI/Qwen/DeepSeek 拒绝。
 */
final class MessageGroups {

    private MessageGroups() {
    }

    /**
     * 队首对话组的长度：从队首起到下一条 UserMessage 之前，没有下一条时为剩余全部。
     */
    static int firstGroupSize(List<Message> messages) {
        for (int i = 1; i < messages.size(); i++) {
            if (messages.get(i) instanceof UserMessage) {
                return i;
            }
        }
        return messages.size();
    }

    /**
     * 从队首起按对话组累计，返回不小于 desired 的最小整组条数。
     * <p>
     * 末尾那一组只有已经收尾（最后一条是不带 tool_call 的 assistant 消息）才算完整；
     * 还在进行中的一轮不能被切走，否则工具响应回来时历史会以孤儿 tool 消息开头。
     * 一组都凑不齐时返回 0。
     */
    static int alignedPrefixSize(List<Message> messages, int desired) {
        int size = 0;
        while (size < desired && size < messages.size()) {
            int groupSize = firstGroupSize(messages.subList(size, messages.size()));
            if (size + groupSize >= messages.size() && !isClosed(messages.get(messages.size() - 1))) {
                return size;
            }
            size += groupSize;
        }
        return size;
    }

    /**
     * 队首到第一条 UserMessage 之间的条数。这段没有对应的用户提问，是被截断的工具链残留，
     * 整段没有 UserMessage 时返回列表长度。
     */
    static int leadingOrphanSize(List<Message> messages) {
        int size = 0;
        while (size < messages.size() && !(messages.get(size) instanceof UserMessage)) {
            size++;
        }
        return size;
    }

    /** 一轮是否已收尾：最后一条是 assistant 的最终回复，而不是 tool_call 请求或工具响应 */
    private static boolean isClosed(Message last) {
        return last instanceof AssistantMessage assistant && !assistant.hasToolCalls();
    }
}

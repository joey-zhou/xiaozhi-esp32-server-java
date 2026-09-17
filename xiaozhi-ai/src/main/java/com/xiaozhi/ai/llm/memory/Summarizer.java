package com.xiaozhi.ai.llm.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 对话超限时，把旧摘要和移出上下文的一批消息合成新摘要。
 */
@FunctionalInterface
public interface Summarizer {

    /**
     * @param summary 上一版摘要，没有时为 null
     * @param batch   要移出上下文的一批完整对话组
     * @return 新摘要；抛异常表示这批消息留在上下文里等下次重试
     */
    String summarize(String summary, List<Message> batch);
}

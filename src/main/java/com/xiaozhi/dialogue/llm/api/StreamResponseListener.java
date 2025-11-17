package com.xiaozhi.dialogue.llm.api;

import org.springframework.ai.chat.metadata.Usage;

/**
 * 流式响应监听器接口
 * 用于处理LLM的流式响应
 */
public interface StreamResponseListener {
    
    /**
     * 当接收到新的token时调用
     * 
     * @param token 接收到的token
     */
    void onToken(String token);
    
    /**
     * 当流式响应完成时调用
     * @param toolName 调用的工具名称，为空时表示没有调用工具
     */
    void onComplete(String toolName, Usage llmUsage);

    /**
     * 当发生错误时调用
     * 
     * @param e 发生的异常
     */
    void onError(Throwable e);
}
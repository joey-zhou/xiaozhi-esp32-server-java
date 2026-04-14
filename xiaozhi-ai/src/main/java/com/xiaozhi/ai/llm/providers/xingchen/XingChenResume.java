package com.xiaozhi.ai.llm.providers.xingchen;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 星辰Agent Resume请求模型
 * 用于工具调用后继续对话
 * 基于文档: https://www.xfyun.cn/doc/spark/Agent04-API%E6%8E%A5%E5%85%A5.html
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XingChenResume {
    /**
     * 事件ID (从工具调用事件中获取)
     */
    @JsonProperty("event_id")
    private String eventId;
    
    /**
     * 事件类型: function_call
     */
    @JsonProperty("event_type")
    private String eventType;
    
    /**
     * 工具调用返回内容
     */
    private String content;
}

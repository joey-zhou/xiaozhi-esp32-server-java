package com.xiaozhi.ai.llm.providers.xingchen;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 星辰Agent API 请求模型
 * 基于文档: https://www.xfyun.cn/doc/spark/Agent04-API%E6%8E%A5%E5%85%A5.html
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XingChenRequest {
    /**
     * 工作流ID (必填)
     */
    @JsonProperty("flow_id")
    private String flowId;
    
    /**
     * 用户ID (必填)
     */
    private String uid;
    
    /**
     * 工作流参数 (必填)
     * key: 工作流中定义的变量名
     * value: 对应的值
     * 
     * 示例: {"AGENT_USER_INPUT": "你好", "func_call": [...]}
     */
    private Map<String, Object> parameters;
    
    /**
     * 扩展信息 (可选)
     */
    private Ext ext;
    
    /**
     * 是否流式返回 (默认false)
     */
    private boolean stream;
    
    /**
     * 会话ID (可选)
     */
    @JsonProperty("chat_id")
    private String chatId;
    
    /**
     * 历史消息 (可选)
     */
    private List<History> history;

    /**
     * 扩展信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ext {
        /**
         * 机器人ID (可选)
         */
        @JsonProperty("bot_id")
        private String botId;
        
        /**
         * 调用方标识 (可选)
         */
        private String caller;
    }

    /**
     * 历史消息记录
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class History {
        /**
         * 角色: user/assistant
         */
        private String role;
        
        /**
         * 内容类型: text
         */
        @JsonProperty("content_type")
        private String contentType;
        
        /**
         * 消息内容
         */
        private String content;
    }
}
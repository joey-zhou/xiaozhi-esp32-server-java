package com.xiaozhi.ai.llm.providers.xingchen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 星辰Agent API 响应模型
 * 基于文档: https://www.xfyun.cn/doc/spark/Agent04-API%E6%8E%A5%E5%85%A5.html
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class XingChenResponse {
    /**
     * 错误码 (0表示成功)
     */
    private Integer code;

    /**
     * 错误消息
     */
    private String message;

    /**
     * 消息ID
     */
    private String id;

    /**
     * 创建时间戳
     */
    private Long created;

    /**
     * 响应选项列表
     */
    private List<Choices> choices;

    /**
     * 事件数据 (工具调用相关)
     */
    @JsonProperty("event_data")
    private EventData eventData;

    /**
     * 响应选项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choices {
        /**
         * 增量内容
         */
        private Delta delta;
        
        /**
         * 选项索引
         */
        private Integer index;

        /**
         * 完成原因: stop/length/null
         */
        @JsonProperty("finish_reason")
        private String finishReason;

        /**
         * 增量消息内容
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Delta {
            /**
             * 角色: assistant
             */
            private String role;
            
            /**
             * 消息内容
             */
            private String content;
        }
    }

    /**
     * 事件数据 (工具调用/中断等场景)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventData {
        /**
         * 事件ID
         */
        @JsonProperty("event_id")
        private String eventId;
        
        /**
         * 事件类型: function_call/interrupt等
         */
        @JsonProperty("event_type")
        private String eventType;
        
        /**
         * 是否需要回复: true/false
         */
        @JsonProperty("need_reply")
        private String needReply;
        
        /**
         * 事件值
         */
        private ReplayValue value;

        /**
         * 回复值
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ReplayValue {
            /**
             * 事件ID
             */
            @JsonProperty("event_id")
            private String eventId;
            
            /**
             * 事件类型
             */
            @JsonProperty("event_type")
            private String eventType;
            
            /**
             * 是否需要回复
             */
            @JsonProperty("need_reply")
            private String needReply;
            
            /**
             * 值
             */
            private String value;
            
            /**
             * 中断类型
             */
            private String type;
            
            /**
             * 中断内容
             */
            private String content;
        }
    }
}

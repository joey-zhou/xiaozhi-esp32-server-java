package com.xiaozhi.event;

import com.xiaozhi.common.domain.AbstractDomainEvent;

/**
 * 工具调用完成事件。
 * 在 XiaoZhiToolCallingManager 执行工具后发布，供日志记录和审计使用。
 */
public class ToolCallCompletedEvent extends AbstractDomainEvent {

    private final String sessionId;
    private final String toolName;
    private final String arguments;
    private final String result;
    private final boolean success;
    private final long durationMs;

    public ToolCallCompletedEvent(Object source, String sessionId, String toolName, String arguments,
                     String result, boolean success, long durationMs) {
        super(source);
        this.sessionId = sessionId;
        this.toolName = toolName;
        this.arguments = arguments;
        this.result = result;
        this.success = success;
        this.durationMs = durationMs;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArguments() {
        return arguments;
    }

    public String getResult() {
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getDurationMs() {
        return durationMs;
    }
}

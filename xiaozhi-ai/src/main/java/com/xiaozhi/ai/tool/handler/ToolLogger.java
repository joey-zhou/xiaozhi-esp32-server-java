package com.xiaozhi.ai.tool.handler;

import com.xiaozhi.event.ToolCallCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 工具调用事件的日志记录处理器。
 * 监听 ToolCallCompletedEvent，记录工具调用的名称、耗时和成功/失败状态。
 */
@Component
public class ToolLogger {

    private static final Logger logger = LoggerFactory.getLogger(ToolLogger.class);

    @EventListener
    public void onToolCallCompletedEvent(ToolCallCompletedEvent event) {
        if (event.isSuccess()) {
            logger.info("工具调用成功 - session: {}, tool: {}, 耗时: {}ms",
                    event.getSessionId(), event.getToolName(), event.getDurationMs());
        } else {
            logger.warn("工具调用失败 - session: {}, tool: {}, 耗时: {}ms, 结果: {}",
                    event.getSessionId(), event.getToolName(), event.getDurationMs(), event.getResult());
        }
    }
}

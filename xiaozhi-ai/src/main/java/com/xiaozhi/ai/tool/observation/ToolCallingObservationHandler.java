package com.xiaozhi.ai.tool.observation;

import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.ai.tool.session.ToolSessionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

/**
 * 工具调用观察处理器，用于捕获工具调用前后的信息。
 * 包括函数名称、参数、callId、工具名称、参数和执行结果。
 */
@Slf4j
@Component
public class ToolCallingObservationHandler implements ObservationHandler<ToolCallingObservationContext> {

    private static final String SESSION_ID_KEY = "sessionId";

    @Autowired(required = false)
    private ToolSessionProvider toolSessionProvider;

    @Override
    public void onStart(ToolCallingObservationContext context) {
        ToolDefinition toolDefinition = context.getToolDefinition();
        String toolName = toolDefinition.name();
        log.info("ToolCalling#onStart: {}", toolName);

        if (!context.containsKey(SESSION_ID_KEY)) {
            return;
        }

        // 获取 sessionId
        Object sessionIdObj = context.get(SESSION_ID_KEY);
        if (!(sessionIdObj instanceof String sessionId)) {
            return;
        }

        // 从 ToolSessionProvider 获取 ToolSession
        if (toolSessionProvider == null) {
            return;
        }
        ToolSession session = toolSessionProvider.getSession(sessionId);
        if (session == null) {
            return;
        }

        // 标记工具调用开始，防止播放器提前sendStop
        session.setToolCalling(true);
    }

    @Override
    public void onStop(ToolCallingObservationContext context) {
        if (!context.containsKey(SESSION_ID_KEY)) {
            return;
        }

        Object sessionIdObj = context.get(SESSION_ID_KEY);
        if (!(sessionIdObj instanceof String sessionId)) {
            return;
        }

        if (toolSessionProvider == null) {
            return;
        }
        ToolSession session = toolSessionProvider.getSession(sessionId);
        if (session == null) {
            return;
        }

        // 清除工具调用状态
        session.setToolCalling(false);
    }

    @Override
    public void onError(ToolCallingObservationContext context) {
        ToolDefinition toolDefinition = context.getToolDefinition();
        String toolName = toolDefinition.name();
        Throwable error = context.getError();
        log.error("ToolCalling#onError - toolName: {}, error: {}", toolName, error.getMessage(), error);

        // 工具调用出错时也要清除toolCalling状态，防止播放器永远不sendStop
        if (context.containsKey(SESSION_ID_KEY)) {
            Object sessionIdObj = context.get(SESSION_ID_KEY);
            if (sessionIdObj instanceof String sessionId && toolSessionProvider != null) {
                ToolSession session = toolSessionProvider.getSession(sessionId);
                if (session != null) {
                    session.setToolCalling(false);
                }
            }
        }
    }

    @Override
    public boolean supportsContext(@NonNull Observation.Context context) {
        return context instanceof ToolCallingObservationContext;
    }

}

package com.xiaozhi.ai.tool.session;

/**
 * 会话查找器 — 替代直接依赖 SessionManager。
 */
public interface ToolSessionProvider {

    ToolSession getSession(String sessionId);

    ToolSession getSessionByDeviceId(String deviceId);
}

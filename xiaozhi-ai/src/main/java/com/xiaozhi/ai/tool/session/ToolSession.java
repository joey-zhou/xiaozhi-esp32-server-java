package com.xiaozhi.ai.tool.session;

import com.xiaozhi.ai.tool.ToolsSessionHolder;

/**
 * 工具会话抽象 — 替代直接依赖 ChatSession。
 * ai 层通过此接口与会话交互，不感知具体通信协议。
 */
public interface ToolSession {

    String getSessionId();

    Integer getRoleId();

    String getDeviceId();

    ToolsSessionHolder getToolsSessionHolder();

    /** 设备 MCP 是否已初始化 */
    boolean isDeviceMcpInitialized();

    void addToolCallDetail(String name, String args, String result);

    void sendTextMessage(String message);

    boolean isOpen();

    /** 标记工具调用状态（防止播放器提前 sendStop） */
    void setToolCalling(boolean calling);
}

package com.xiaozhi.dialogue.adapter;

import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.playback.Player;

/**
 * ChatSession 到 ToolSession 的适配器。
 * 隔离 ai 层与通信层，ai 层只能访问工具调用相关的方法，
 * 看不到 audioSinks、DeviceState、IotDescriptor 等通信层细节。
 */
public class ChatSessionToolAdapter implements ToolSession {

    private final ChatSession chatSession;

    public ChatSessionToolAdapter(ChatSession chatSession) {
        this.chatSession = chatSession;
    }

    @Override
    public String getSessionId() {
        return chatSession.getSessionId();
    }

    @Override
    public Integer getRoleId() {
        return chatSession.getDevice() != null ? chatSession.getDevice().getRoleId() : null;
    }

    @Override
    public String getDeviceId() {
        return chatSession.getDevice() != null ? chatSession.getDevice().getDeviceId() : null;
    }

    @Override
    public ToolsSessionHolder getToolsSessionHolder() {
        return chatSession.getToolsSessionHolder();
    }

    @Override
    public boolean isDeviceMcpInitialized() {
        return chatSession.getDeviceMcpHolder() != null && chatSession.getDeviceMcpHolder().isMcpInitialized();
    }

    @Override
    public void addToolCallDetail(String name, String args, String result) {
        chatSession.addToolCallDetail(name, args, result);
    }

    @Override
    public void sendTextMessage(String message) {
        chatSession.sendTextMessage(message);
    }

    @Override
    public boolean isOpen() {
        return chatSession.isOpen();
    }

    @Override
    public void setToolCalling(boolean calling) {
        Player player = chatSession.getPlayer();
        if (player != null) {
            player.setToolCalling(calling);
        }
    }
}

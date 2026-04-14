package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.playback.Player;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对话上下文，承载一次对话会话中与对话逻辑直接相关的状态。
 * 从 ChatSession（通信层）中拆分出来，使通信层不再承载对话业务逻辑。
 */
@Getter
@Setter
public class DialogueContext {

    private Persona persona;

    private final AtomicReference<Player> playerRef = new AtomicReference<>();

    private ToolsSessionHolder toolsSessionHolder;

    /**
     * 当前对话轮次的用户音频保存路径，供 Function 复用
     */
    private volatile Path userAudioPath;

    /**
     * 当前对话轮次中的工具调用详情列表（包括内置Function和MCP工具）
     * 由 XiaoZhiToolCallingManager 在执行工具时追加，由 Persona 在消息保存后清空。
     */
    private final List<ToolCallInfo> toolCallDetails = new CopyOnWriteArrayList<>();

    /**
     * 工具调用详情
     */
    public record ToolCallInfo(String name, String arguments, String result) {}

    public Player getPlayer() {
        return playerRef.get();
    }

    public void setPlayer(Player player) {
        playerRef.set(player);
    }

    public void addToolCallDetail(String name, String arguments, String result) {
        toolCallDetails.add(new ToolCallInfo(name, arguments, result));
    }

    public synchronized List<ToolCallInfo> drainToolCallDetails() {
        List<ToolCallInfo> details = new ArrayList<>(toolCallDetails);
        toolCallDetails.clear();
        return details;
    }

    public boolean isFunctionCalled() {
        return !toolCallDetails.isEmpty();
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolsSessionHolder.getAllFunction();
    }
}

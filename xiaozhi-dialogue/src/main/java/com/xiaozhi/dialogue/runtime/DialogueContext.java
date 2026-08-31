package com.xiaozhi.dialogue.runtime;

import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.playback.Player;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对话上下文，承载一次对话会话中与对话逻辑直接相关的状态。
 * 从 ChatSession（通信层）中拆分出来，使通信层不再承载对话业务逻辑。
 */
@Slf4j
@Getter
@Setter
public class DialogueContext {

    private Persona persona;

    private final AtomicReference<Player> playerRef = new AtomicReference<>();

    private ToolsSessionHolder toolsSessionHolder;

    /**
     * 当前对话轮次的用户音频本地文件路径（WAV），供 Function 在本轮内即时读取。
     * 上传云存储后本地临时文件可能已被删除。
     */
    private volatile Path userAudioPath;

    /**
     * 当前对话轮次用户音频的持久化存储路径：本地为相对路径，云存储为完整 URL。
     * 与 {@link #userAudioPath} 区分——后者是 java.nio.file.Path（会破坏 URL 的双斜杠），
     * 此处始终为原始字符串，用于写入 sys_message.audioPath。
     */
    private volatile String userAudioStoredPath;

    /**
     * 当前对话轮次用户音频时长（秒）。必须在上传云存储前用本地文件算好，
     * 因为上传后本地文件会被删除，且云端 storedPath 无法当作本地文件读取。
     */
    private volatile double sttDuration = -1;

    /**
     * 当前对话轮次中的工具调用详情列表（包括内置Function和MCP工具）
     * 由 XiaoZhiToolCallingManager 在执行工具时追加，由 Persona 在轮次开始时重置。
     */
    private final List<ToolCallInfo> toolCallDetails = new ArrayList<>();

    /**
     * 当前对话轮次中模型真实调用的工具链（tool_call 请求 + 工具执行结果）。
     * 一轮 tool loop 可能触发多次工具调用，故按顺序累积而非覆盖，否则只有最后一次能被持久化。
     */
    private final List<ToolChainPair> toolChains = new ArrayList<>();

    /**
     * 当前对话轮次标识，复用 toolContext 中已有的 conversationTimestamp。
     * <p>
     * 打断只会 dispose LLM 的 Flux，不会中断已在执行的 {@code toolCallback.call()}（同步阻塞、独立线程）。
     * 该工具仍会跑完并回写记录，若此时新一轮已经开始，记录就会错误地挂到新一轮上。
     * 因此工具调用发起时捕获当轮标识，回写前校验轮次未变，变了则丢弃 —— 与
     * {@link com.xiaozhi.dialogue.playback.ScheduledPlayer} 丢弃跨代残帧的做法一致。
     */
    private long currentTurnId;

    /**
     * 工具调用详情
     */
    public record ToolCallInfo(String name, String arguments, String result) {}

    public record ToolCallSnapshot(List<ToolCallInfo> details, List<ToolChainPair> chains) {}

    public Player getPlayer() {
        return playerRef.get();
    }

    public void setPlayer(Player player) {
        playerRef.set(player);
    }

    public synchronized void addToolCallDetail(Long turnId, String name, String arguments, String result) {
        if (isStaleTurn(turnId, name)) {
            return;
        }
        toolCallDetails.add(new ToolCallInfo(name, arguments, result));
    }

    public synchronized void addToolChain(Long turnId, AssistantMessage assistantMessage, ToolResponseMessage responseMessage) {
        if (isStaleTurn(turnId, "toolChain")) {
            return;
        }
        toolChains.add(new ToolChainPair(assistantMessage, responseMessage));
    }

    /**
     * 校验工具调用是否属于已过期的轮次。
     * turnId 为 null 时（非对话链路发起的调用）不做校验，按当前轮处理。
     */
    private boolean isStaleTurn(Long turnId, String toolName) {
        if (turnId == null) {
            return false;
        }
        if (turnId.longValue() != currentTurnId) {
            log.debug("轮次已切换，丢弃过期的工具调用记录: tool={}, turnId={}, currentTurnId={}",
                    toolName, turnId, currentTurnId);
            return true;
        }
        return false;
    }

    public synchronized ToolCallSnapshot snapshotToolCalls(long turnId) {
        if (currentTurnId != turnId) {
            return null;
        }
        return new ToolCallSnapshot(List.copyOf(toolCallDetails), List.copyOf(toolChains));
    }

    /**
     * 开始新轮次并重置工具调用状态。必须在每轮对话开始时调用，而非在轮次结束时清理：
     * 用户打断会 dispose LLM 的 Flux，轮次结束的聚合回调不会执行，残留记录会被
     * 累积到下一个正常完成的轮次上（表现为"一次回复里挂了好几次工具调用"）。
     * 在入口重置可覆盖 cancel / error / 超时等所有异常终止路径。
     *
     */
    public synchronized void startTurn(long turnId) {
        toolCallDetails.clear();
        toolChains.clear();
        currentTurnId = turnId;
    }

    public synchronized boolean isFunctionCalled() {
        return !toolCallDetails.isEmpty();
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolsSessionHolder.getAllFunction();
    }
}

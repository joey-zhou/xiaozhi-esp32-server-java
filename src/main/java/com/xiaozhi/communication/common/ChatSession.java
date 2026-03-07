package com.xiaozhi.communication.common;

import com.xiaozhi.communication.domain.iot.IotDescriptor;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpHolder;
import com.xiaozhi.dialogue.service.Player;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.dialogue.service.Persona;
import lombok.Data;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public abstract class ChatSession {
    /**
     * 当前会话的 sessionId
     */
    protected String sessionId;
    /**
     * 设备信息
     */
    protected SysDevice sysDevice;
    /**
     * 设备可用角色列表
     */
    protected List<SysRole> sysRoleList;

    protected Persona persona;

    /**
     * 设备 iot 信息
     */
    protected Map<String, IotDescriptor> iotDescriptors = new HashMap<>();
    /**
     * 当前 session 的 function 控制器
     */
    protected ToolsSessionHolder toolsSessionHolder;

    /**
     * 是否正在播放音乐
     */
    protected boolean musicPlaying;
    /**
     * 是否正在说话
     */
    protected boolean playing;
    /**
     * 是否正在唤醒响应中 (播放唤醒音效和欢迎语)
     * 在此期间应该忽略 VAD 检测，避免被唤醒词音频误触发打断
     */
    protected volatile boolean inWakeupResponse = false;

    /**
     * 构造函数
     * @param sessionId 会话 ID
     */
    protected ChatSession(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 默认构造函数
     */
    protected ChatSession() {
    }
    /**
     * 设备状态 (auto, realTime)
     */
    protected ListenMode mode;
    /**
     * 会话的音频数据流
     */
    protected Sinks.Many<byte[]> audioSinks;
    /**
     * 会话是否正在进行流式识别
     */
    protected boolean streamingState;
    /**
     * 会话的最后有效活动时间
     */
    protected Instant lastActivityTime;

    /**
     * 当前对话轮次中的工具调用详情列表
     */
    protected final List<ToolCallInfo> toolCallDetails = new CopyOnWriteArrayList<>();

    /**
     * 工具调用详情
     */
    public record ToolCallInfo(String name, String arguments, String result) {}

    public void addToolCallDetail(String name, String arguments, String result) {
        toolCallDetails.add(new ToolCallInfo(name, arguments, result));
    }

    public List<ToolCallInfo> drainToolCallDetails() {
        List<ToolCallInfo> details = new ArrayList<>(toolCallDetails);
        toolCallDetails.clear();
        return details;
    }

    public boolean isFunctionCalled() {
        return !toolCallDetails.isEmpty();
    }

    /**
     * 获取播放器
     * @return 播放器
     */
    public Player getPlayer() {
        if (persona != null) {
            return persona.getPlayer();
        }
        return null;
    }

    /**
     * 获取会话 ID
     * @return 会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取设备信息
     * @return 设备信息
     */
    public SysDevice getSysDevice() {
        return sysDevice;
    }

    /**
     * 获取设备可用角色列表
     * @return 设备可用角色列表
     */
    public List<SysRole> getSysRoleList() {
        return sysRoleList;
    }

    /**
     * 获取 Persona
     * @return Persona
     */
    public Persona getPersona() {
        return persona;
    }

    /**
     * 获取设备 iot 信息
     * @return 设备 iot 信息
     */
    public Map<String, IotDescriptor> getIotDescriptors() {
        return iotDescriptors;
    }

    /**
     * 获取当前 session 的 function 控制器
     * @return function 控制器
     */
    public ToolsSessionHolder getFunctionSessionHolder() {
        return toolsSessionHolder;
    }

    /**
     * 是否正在播放音乐
     * @return 是否正在播放音乐
     */
    public boolean isMusicPlaying() {
        return musicPlaying;
    }

    /**
     * 是否正在说话
     * @return 是否正在说话
     */
    public boolean isPlaying() {
        return playing;
    }

    /**
     * 是否正在唤醒响应中
     * @return 是否正在唤醒响应中
     */
    public boolean isInWakeupResponse() {
        return inWakeupResponse;
    }

    /**
     * 获取设备状态
     * @return 设备状态
     */
    public ListenMode getMode() {
        return mode;
    }

    /**
     * 获取会话的音频数据流
     * @return 音频数据流
     */
    public Sinks.Many<byte[]> getAudioSinks() {
        return audioSinks;
    }

    /**
     * 会话是否正在进行流式识别
     * @return 是否正在进行流式识别
     */
    public boolean isStreamingState() {
        return streamingState;
    }

    /**
     * 获取会话的最后有效活动时间
     * @return 最后有效活动时间
     */
    public Instant getLastActivityTime() {
        return lastActivityTime;
    }

    /**
     * 获取工具调用详情列表
     * @return 工具调用详情列表
     */
    public List<ToolCallInfo> getToolCallDetails() {
        return toolCallDetails;
    }

    /**
     * 获取描述符列表
     * @return 描述符列表
     */
    public List<IotDescriptor> getDescriptors() {
        return new ArrayList<>(iotDescriptors.values());
    }

    /**
     * 清除音频数据流
     */
    public void clearAudioSinks() {
        if (audioSinks != null) {
            setStreamingState(false);
            setAudioSinks(null);
        }
    }

    /**
     * 获取最后一条消息的时间戳
     * @return 最后一条消息的时间戳
     */
    public Instant getLastMessageTimestamp() {
        // TODO: 从 conversation 获取最后一条消息的时间戳
        return null;
    }

    /**
     * 获取会话创建时间
     * @return 会话创建时间
     */
    public LocalDateTime getCreateTime() {
        if (lastActivityTime != null) {
            return LocalDateTime.ofInstant(lastActivityTime, ZoneId.systemDefault());
        }
        return LocalDateTime.now();
    }

    /**
     * 格式化创建时间
     * @return 格式化后的创建时间
     */
    public String getFormattedCreateTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return getCreateTime().format(formatter);
    }

    /**
     * 计算会话持续时间（分钟）
     * @return 持续时间（分钟）
     */
    public long getDurationMinutes() {
        if (lastActivityTime != null) {
            return ChronoUnit.MINUTES.between(lastActivityTime, Instant.now());
        }
        return 0;
    }

    /**
     * 获取音频文件路径
     * @return 音频文件路径
     */
    public java.nio.file.Path getAudioFilePath() {
        if (persona != null) {
            return persona.getAudioFilePath();
        }
        return null;
    }

    /**
     * 保存音频文件
     * @param audioData 音频数据
     * @return 音频文件路径
     */
    public Path saveAudioFile(byte[] audioData) {
        // TODO: 实现音频文件保存逻辑
        return null;
    }

    /**
     * 获取音频文件路径
     * @param messageType 消息类型
     * @param timestamp 时间戳
     * @return 音频文件路径
     */
    public Path getAudioPath(String messageType, Instant timestamp) {
        // TODO: 实现音频文件路径生成逻辑
        return null;
    }

    /**
     * 获取 MCP 工具回调列表
     * @return MCP 工具回调列表
     */
    public List<ToolCallback> getMcpToolCallbacks() {
        if (toolsSessionHolder != null) {
            return toolsSessionHolder.getMcpToolCallbacks();
        }
        return new ArrayList<>();
    }

    /**
     * 获取工具回调列表
     * @return 工具回调列表
     */
    public List<ToolCallback> getToolCallbacks() {
        if (toolsSessionHolder != null) {
            return toolsSessionHolder.getAllFunction();
        }
        return new ArrayList<>();
    }

    /**
     * 设置 Function Session Holder
     * @param toolsSessionHolder toolsSessionHolder
     */
    public void setFunctionSessionHolder(ToolsSessionHolder toolsSessionHolder) {
        this.toolsSessionHolder = toolsSessionHolder;
    }

    /**
     * 设置 Player
     * @param player player
     */
    public void setPlayer(Player player) {
        // 由子类实现或不需要
    }

    /**
     * 获取设备 MCP 保持者
     * @return 设备 MCP 保持者
     */
    public DeviceMcpHolder getDeviceMcpHolder() {
        if (toolsSessionHolder != null) {
            return toolsSessionHolder.getDeviceMcpHolder();
        }
        return null;
    }

    /**
     * 检查会话是否打开
     * @return 是否打开
     */
    public abstract boolean isOpen();

    /**
     * 关闭会话
     */
    public abstract void close();

    /**
     * 发送文本消息
     * @param message 消息内容
     */
    public abstract void sendTextMessage(String message);

    /**
     * 发送二进制消息
     * @param message 消息内容
     */
    public abstract void sendBinaryMessage(byte[] message);
}

package com.xiaozhi.communication.common;

import com.xiaozhi.communication.domain.iot.IotDescriptor;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.ai.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpHolder;
import com.xiaozhi.dialogue.runtime.DialogueContext;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.dialogue.runtime.Persona;
import lombok.Data;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public abstract class ChatSession {
    /**
     * 当前会话的sessionId
     */
    protected String sessionId;
    /**
     * 设备信息
     */
    protected DeviceBO device;

    /**
     * 对话上下文，承载与对话逻辑直接相关的状态（Persona、Player、工具回调等）。
     * 内部实现细节，外部通过本类的直通方法访问。
     */
    private DialogueContext dialogueContext;

    /**
     * 设备iot信息
     */
    protected Map<String, IotDescriptor> iotDescriptors = new ConcurrentHashMap<>();

    /**
     * 设备服务端状态机。
     * 替代原有的 playing / musicPlaying / streamingState / inWakeupResponse 分散布尔字段。
     * 只有 IDLE 状态才允许触发不活跃超时。
     */
    private volatile DeviceState deviceState = DeviceState.IDLE;

    /**
     * 状态转换方法
     * 包含状态转换验证和日志
     */
    public void transitionTo(DeviceState newState) {
        if (newState == null) {
            return;
        }
        DeviceState oldState = this.deviceState;
        if (oldState == newState) {
            return;
        }
        this.deviceState = newState;
        log.debug("状态转换: {} -> {} (SessionId: {})", oldState, newState, sessionId);
    }

    /**
     * 设备状态(auto, realTime)
     */
    protected ListenMode mode;
    /**
     * 会话的音频数据流。
     * 保留在 ChatSession 而非 Persona：audioSinks 是 VAD 驱动的音频输入缓冲，
     * 生命周期跟"用户说话的起止"绑定（生产者是 DialogueService/VAD，消费者是 STT），
     * 属于传输层关注，与 Persona（AI 能力运行时）生命周期不同。
     * 移入 Persona 会增加 null 判断复杂度而无收益。
     */
    protected volatile Sinks.Many<byte[]> audioSinks;
    /**
     * 会话的最后有效活动时间
     */
    protected volatile Instant lastActivityTime;

    // ========== 对话层直通方法（内部委托给 dialogueContext，外部无需感知） ==========

    public Persona getPersona()                 { return dialogueContext.getPersona(); }
    public void setPersona(Persona persona)     { dialogueContext.setPersona(persona); }

    public Player getPlayer()                   { return dialogueContext.getPlayer(); }
    public void setPlayer(Player player)        { dialogueContext.setPlayer(player); }

    public Path getUserAudioPath()              { return dialogueContext.getUserAudioPath(); }
    public void setUserAudioPath(Path path)     { dialogueContext.setUserAudioPath(path); }

    public ToolsSessionHolder getToolsSessionHolder()                          { return dialogueContext.getToolsSessionHolder(); }
    public void setToolsSessionHolder(ToolsSessionHolder h)                    { dialogueContext.setToolsSessionHolder(h); }
    public List<ToolCallback> getToolCallbacks()                               { return dialogueContext.getToolCallbacks(); }
    public void addToolCallDetail(String name, String args, String result)     { dialogueContext.addToolCallDetail(name, args, result); }
    public List<DialogueContext.ToolCallInfo> drainToolCallDetails()           { return dialogueContext.drainToolCallDetails(); }
    public boolean isFunctionCalled()                                          { return dialogueContext.isFunctionCalled(); }

    // ========== 超时断连标记 ==========
    private volatile boolean timeoutDisconnect;

    // --------------------设备mcp-------------------------
    private DeviceMcpHolder deviceMcpHolder = new DeviceMcpHolder();

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.lastActivityTime = Instant.now();
        this.dialogueContext = new DialogueContext();
    }

    public void clearAudioSinks(){
        // 清理音频流
        Sinks.Many<byte[]> sink = getAudioSinks();
        if (sink != null) {
            sink.tryEmitComplete();
        }
        // 重置会话状态
        deviceState = DeviceState.IDLE;
        setAudioSinks(null);
    }

    // ========== 音频流管理方法（从 SessionManager 迁入） ==========

    /**
     * 创建新的音频数据流
     */
    public void createAudioStream() {
        this.audioSinks = Sinks.many().multicast().onBackpressureBuffer();
    }

    /**
     * 发送音频数据到流
     */
    public void sendAudioData(byte[] data) {
        Sinks.Many<byte[]> sink = audioSinks; // 局部变量避免 TOCTOU
        if (sink != null) {
            sink.tryEmitNext(data);
        }
    }

    /**
     * 完成音频流（通知下游数据发送完毕）
     */
    public void completeAudioStream() {
        if (audioSinks != null) {
            audioSinks.tryEmitComplete();
        }
    }

    /**
     * 关闭音频流（释放引用）
     */
    public void closeAudioStream() {
        this.audioSinks = null;
    }

    /**
     * 音频文件约定路径为：audio/{date}/{device-id}/{role-id}/{timestamp}-{who}.wav|ogg
     * 按日期分目录，便于批量清理过期数据（直接删整个日期目录）
     *
     * @param who
     * @param instant
     * @return
     */
    public Path getAudioPath(String who, Instant instant) {

        instant = instant.truncatedTo(ChronoUnit.SECONDS);

        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        String date = localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String datetime = localDateTime.format(DateTimeFormatter.ISO_DATE_TIME).replace(":", "");
        DeviceBO device = this.getDevice();
        // 判断设备ID是否有不适合路径的特殊字符，它很可能是mac地址需要转换。
        String deviceId = device.getDeviceId().replace(":", "-");
        String roleId = device.getRoleId().toString();
        String extension = MessageBO.SENDER_USER.equals(who) ? "wav" : "ogg";
        String filename = "%s-%s.%s".formatted(datetime, who, extension);
        return Path.of(AudioUtils.AUDIO_PATH, date, deviceId, roleId, filename);
    }

    /**
     * 会话连接是否打开中
     *
     * @return
     */
    public abstract boolean isOpen();

    /**
     * 音频通道是否打开可用
     *
     * @return
     */
    public abstract boolean isAudioChannelOpen();

    public abstract void close();

    public abstract void sendTextMessage(String message);

    public abstract void sendBinaryMessage(byte[] message);

    public boolean isTimeoutDisconnect()            { return timeoutDisconnect; }
    public void setTimeoutDisconnect(boolean flag)  { this.timeoutDisconnect = flag; }

    /**
     * 平台主动下发helloMessage
     * 一般用于会话激活
     */
    public void sendHelloMessage() {}
}

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
     * 当前会话的sessionId
     */
    protected String sessionId;
    /**
     * 设备信息
     */
    protected SysDevice sysDevice;
    /**
     * 设备可用角色列表
     * TODO ChangeRoleFunction.java 已从数据库中获取，所以这里实际没有被用到。
     */
    protected List<SysRole> sysRoleList;

    protected Persona persona;

    /**
     * 设备iot信息
     */
    protected Map<String, IotDescriptor> iotDescriptors = new HashMap<>();
    /**
     * 当前session的function控制器
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
     * 是否正在唤醒响应中(播放唤醒音效和欢迎语)
     * 在此期间应该忽略VAD检测,避免被唤醒词音频误触发打断
     */
    protected volatile boolean inWakeupResponse = false;
    /**
     * 设备状态(auto, realTime)
     */
    protected ListenMode mode;
    /**
     * 会话的音频数据流
     * TODO 也考虑改为在Persona中处理。
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
     * 当前对话轮次中的工具调用详情列表（包括内置Function和MCP工具）
     * 由 XiaoZhiToolCallingManager 在执行工具时追加，由 Persona 在消息保存后清空。
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
     * 最近一次对话的模型响应时间及TTS响应时间
     */
    public static final String ATTR_FIRST_TTS_RESPONSE_TIME = "firstTtsResponseTime";
    /**
     * 会话属性存储
     */
    protected final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    // --------------------设备mcp-------------------------
    private DeviceMcpHolder deviceMcpHolder = new DeviceMcpHolder();

    // 可能会有多个线程判断是否为空。 所以这里使用volatile修饰，避免Player初始化多个从而有多个播放器工作线程。
    //  TODO synchronized 或AtomicReference可能更好。
    protected volatile Player player;

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.lastActivityTime = Instant.now();
    }

    public void setAttribute(String key, Object value) {
        if (value != null) {
            attributes.put(key, value);
        } else {
            attributes.remove(key);
        }
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public void clearAudioSinks(){
        // 清理音频流
        Sinks.Many<byte[]> sink = getAudioSinks();
        if (sink != null) {
            sink.tryEmitComplete();
        }
        // 重置会话状态
        setStreamingState(false);
        setAudioSinks(null);
    }

    /**
     * 音频文件约定路径为：audio/{device-id}/{role-id}/{timestamp}-user.wav
     * {device-id}/{role-id}/{timestamp}-user 能确定唯一性，不会有并发的麻烦。
     * 除非多设备在嵌入式软件里强行修改mac地址（deviceId目前是基于mac地址的)
     *
     * @param who
     * @return
     */
    public Path getAudioPath(String who, Instant instant) {

        instant = instant.truncatedTo(ChronoUnit.SECONDS);

        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        String datetime = localDateTime.format(DateTimeFormatter.ISO_DATE_TIME).replace(":", "");
        SysDevice device = this.getSysDevice();
        // 判断设备ID是否有不适合路径的特殊字符，它很可能是mac地址需要转换。
        String deviceId = device.getDeviceId().replace(":", "-");
        String roleId = device.getRoleId().toString();
        String extension = Conversation.MESSAGE_TYPE_USER.equals(who) ? "wav" : "opus";
        String filename = "%s-%s.%s".formatted(datetime, who, extension);
        Path path = Path.of(AudioUtils.AUDIO_PATH, deviceId, roleId, filename);
        return path;
    }

    public ToolsSessionHolder getFunctionSessionHolder() {
        return toolsSessionHolder;
    }

    public void setFunctionSessionHolder(ToolsSessionHolder toolsSessionHolder) {
        this.toolsSessionHolder = toolsSessionHolder;
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolsSessionHolder.getAllFunction();
    }

    /**
     * 会话连接是否打开中
     *
     * @return
     */
    public abstract boolean isOpen();

    public abstract void close();

    public abstract void sendTextMessage(String message);

    public abstract void sendBinaryMessage(byte[] message);

}

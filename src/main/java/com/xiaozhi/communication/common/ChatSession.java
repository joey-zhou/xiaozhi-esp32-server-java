package com.xiaozhi.communication.common;

import com.xiaozhi.communication.domain.iot.IotDescriptor;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.llm.tool.mcp.device.DeviceMcpHolder;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.utils.AudioUtils;
import lombok.Data;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     */
    protected List<SysRole> sysRoleList;
    /**
     * 一个Session在某个时刻，只有一个活跃的Conversation。
     * 当切换角色时，Conversation应该释放新建。切换角色一般是不频繁的。
     */
    protected Conversation conversation;
    /**
     * 设备iot信息
     */
    protected Map<String, IotDescriptor> iotDescriptors = new HashMap<>();
    /**
     * 当前session的function控制器
     */
    protected ToolsSessionHolder toolsSessionHolder;

    /**
     * 当前语音发送完毕后，是否关闭session
     */
    protected boolean closeAfterChat;
    /**
     * 是否正在播放音乐
     */
    protected boolean musicPlaying;
    /**
     * 是否正在说话
     */
    protected boolean playing;
    /**
     * 设备状态（auto, realTime)
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
     * 当前session是否支持function_call
     */
    protected boolean supportFunctionCall = true;

    /**
     * 最近一次对话的模型响应时间及TTS响应时间
     */
    public static final String ATTR_FIRST_MODEL_RESPONSE_TIME = "firstModelResponseTime";
    public static final String ATTR_FIRST_TTS_RESPONSE_TIME = "firstTtsResponseTime";
    /**
     * 会话属性存储
     */
    protected final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    // --------------------设备mcp-------------------------
    private DeviceMcpHolder deviceMcpHolder = new DeviceMcpHolder();

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.lastActivityTime = Instant.now();
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public void setAssistantTimeMillis(Long assistantTimeMillis) {
        setAttribute("assistantTimeMillis", assistantTimeMillis);
    }

    public Long getAssistantTimeMillis() {
        return (Long) getAttribute("assistantTimeMillis");
    }

    public void setUserTimeMillis(Long userTimeMillis) {
        setAttribute("userTimeMillis", userTimeMillis);
    }

    public Long getUserTimeMillis() {
        return (Long) getAttribute("userTimeMillis");
    }

    /**
     * 音频文件约定路径为：audio/{device-id}/{role-id}/{timestamp}-user.wav
     * {device-id}/{role-id}/{timestamp}-user 能确定唯一性，不会有并发的麻烦。
     * 除非多设备在嵌入式软件里强行修改mac地址（deviceId目前是基于mac地址的)
     *
     * @param who
     * @return
     */
    private Path getAudioPath(String who, Long timeMillis) {

        Instant instant = Instant.ofEpochMilli(timeMillis).truncatedTo(ChronoUnit.SECONDS);

        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        String datetime = localDateTime.format(DateTimeFormatter.ISO_DATE_TIME).replace(":", "");
        SysDevice device = this.getSysDevice();
        // 判断设备ID是否有不适合路径的特殊字符，它很可能是mac地址需要转换。
        String deviceId = device.getDeviceId().replace(":", "-");
        String roleId = device.getRoleId().toString();
        String filename = "%s-%s.wav".formatted(datetime, who);
        Path path = Path.of(AudioUtils.AUDIO_PATH, deviceId, roleId, filename);
        return path;
    }

    public Path getUserAudioPath() {
        return getAudioPath(Conversation.MESSAGE_TYPE_USER, this.getUserTimeMillis());
    }

    public Path getAssistantAudioPath() {
        return getAudioPath(Conversation.MESSAGE_TYPE_ASSISTANT, getAssistantTimeMillis());
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

    /**
     * 音频通道是否打开可用
     *
     * @return
     */
    public abstract boolean isAudioChannelOpen();

    public abstract void close();

    public abstract void sendTextMessage(String message);

    public abstract void sendBinaryMessage(byte[] message);

    /**
     * 设置 Conversation，需要与当前活跃角色一致。
     * 当切换角色时，会释放当前 Conversation，并新建一个对应于新角色的Conversation。
     *
     * @param conversation
     */
    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    /**
     * 获取与当前活跃角色一致的 Conversation。
     *
     * @return
     */
    public Conversation getConversation() {
        return conversation;
    }
}

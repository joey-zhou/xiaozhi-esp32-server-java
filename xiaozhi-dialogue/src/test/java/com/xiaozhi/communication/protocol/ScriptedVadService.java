package com.xiaozhi.communication.protocol;

import com.xiaozhi.dialogue.audio.VadService;
import com.xiaozhi.event.TtsPlaybackCompletedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 脚本化 VAD 假体，彻底绕开 ONNX 与 SileroVadModel。
 *
 * <p>断句结果不由音频内容决定，而是读上行帧的第 0 个字节当脚本标记：
 * 0 = NO_SPEECH，1 = SPEECH_START，2 = SPEECH_CONTINUE，3 = SPEECH_END。
 * 用例通过 {@code FakeDevice.speak(SPEECH_START, SPEECH_CONTINUE, SPEECH_END)} 编排断句时序。
 *
 * <p>保留的真实语义（用例可以依赖）：
 * <ul>
 *   <li>会话未 initSession 时 processAudio 返回 null，上层据此走唤醒词前置缓冲；</li>
 *   <li>initSession 的 autoSegment 参数如实记录，可断言 manual 与 auto/realtime 的分流；</li>
 *   <li>finishSegment 只在本轮确有语音进行中时返回 true。</li>
 * </ul>
 *
 * <p>盲区：VAD 断句质量、能量/概率阈值、前置缓冲与静音裁剪、GRU 状态收敛全部不模拟，
 * 用例不要在协议套件里断言这些，它们属于另开的带音频样本的慢测。
 */
class ScriptedVadService extends VadService {

    static final byte NO_SPEECH = 0;
    static final byte SPEECH_START = 1;
    static final byte SPEECH_CONTINUE = 2;
    static final byte SPEECH_END = 3;

    private final Map<String, Boolean> autoSegment = new ConcurrentHashMap<>();
    private final Map<String, Boolean> speaking = new ConcurrentHashMap<>();
    private final Map<String, List<byte[]>> pcm = new ConcurrentHashMap<>();
    private final List<String> modelStateResets = new CopyOnWriteArrayList<>();
    private final List<Long> echoTimestamps = new CopyOnWriteArrayList<>();
    private final List<byte[]> processedFrames = new CopyOnWriteArrayList<>();

    @Override
    public void initSession(String sessionId) {
        initSession(sessionId, true);
    }

    @Override
    public void initSession(String sessionId, boolean autoSegmentEnabled) {
        autoSegment.put(sessionId, autoSegmentEnabled);
        speaking.put(sessionId, false);
        pcm.put(sessionId, new CopyOnWriteArrayList<>());
    }

    @Override
    public boolean isSessionInitialized(String sessionId) {
        return autoSegment.containsKey(sessionId);
    }

    @Override
    public VadResult processAudio(String sessionId, byte[] opusData) {
        return processAudio(sessionId, opusData, 0);
    }

    @Override
    public VadResult processAudio(String sessionId, byte[] opusData, long echoTimestamp) {
        if (!isSessionInitialized(sessionId)) {
            return null;
        }
        processedFrames.add(opusData);
        echoTimestamps.add(echoTimestamp);
        byte marker = opusData.length > 0 ? opusData[0] : NO_SPEECH;
        return switch (marker) {
            case SPEECH_START -> {
                speaking.put(sessionId, true);
                pcm.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(opusData);
                yield new VadResult(VadStatus.SPEECH_START, opusData);
            }
            case SPEECH_CONTINUE -> {
                pcm.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(opusData);
                yield new VadResult(VadStatus.SPEECH_CONTINUE, opusData);
            }
            case SPEECH_END -> {
                speaking.put(sessionId, false);
                pcm.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(opusData);
                yield new VadResult(VadStatus.SPEECH_END, opusData);
            }
            default -> new VadResult(VadStatus.NO_SPEECH, null);
        };
    }

    @Override
    public void onTtsPlaybackEnd(TtsPlaybackCompletedEvent event) {
        resetVadModelState(event.getSessionId());
    }

    @Override
    public void resetVadModelState(String sessionId) {
        modelStateResets.add(sessionId);
    }

    @Override
    public void resetSession(String sessionId) {
        autoSegment.remove(sessionId);
        speaking.remove(sessionId);
        pcm.remove(sessionId);
    }

    @Override
    public boolean finishSegment(String sessionId) {
        Boolean current = speaking.get(sessionId);
        if (current == null || !current) {
            return false;
        }
        speaking.put(sessionId, false);
        return true;
    }

    @Override
    public List<byte[]> getPcmData(String sessionId) {
        return new ArrayList<>(pcm.getOrDefault(sessionId, List.of()));
    }

    // ========== 断言入口 ==========

    /** 该会话 initSession 时声明的自动断句标志，未初始化返回 null */
    Boolean autoSegmentOf(String sessionId) {
        return autoSegment.get(sessionId);
    }

    /** 进过 VAD 的上行帧，按到达顺序 */
    List<byte[]> processedFrames() {
        return List.copyOf(processedFrames);
    }

    /** 上行帧携带的设备回显时间戳，与 {@link #processedFrames()} 一一对应 */
    List<Long> echoTimestamps() {
        return List.copyOf(echoTimestamps);
    }

    /** 最近一次上行帧的回显时间戳，无上行帧时返回 null */
    Long lastEchoTimestamp() {
        return echoTimestamps.isEmpty() ? null : echoTimestamps.get(echoTimestamps.size() - 1);
    }

    /** 收到 TtsPlaybackCompletedEvent 而重置模型状态的会话列表 */
    List<String> modelStateResets() {
        return List.copyOf(modelStateResets);
    }
}

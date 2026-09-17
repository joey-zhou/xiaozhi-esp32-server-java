package com.xiaozhi.dialogue.audio;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.dialogue.audio.vad.VadModel.InferenceResult;
import com.xiaozhi.dialogue.audio.vad.SileroVadModel;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.OpusProcessor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.xiaozhi.event.TtsPlaybackCompletedEvent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
/**
 * 语音活动检测服务
 */
@Slf4j
@Service
public class VadService {
    // VadState 自身即为该会话的锁对象，避免锁对象与状态分别存放在两张 map 里导致的错锁竞态
    private final ConcurrentHashMap<String, VadState> states = new ConcurrentHashMap<>();

    @Value("${xiaozhi.vad.prebuffer.ms:500}")
    private int preBufferMs;

    @Value("${xiaozhi.vad.tail.keep.ms:300}")
    private int tailKeepMs;

    // STT 单段时长：一条识别流从开口起最多等 90 秒，说到这个时长就换一条流继续，各段文本最后拼接。
    // 必须留在 90 秒之内，否则识别流先超时把这一段作废
    @Value("${xiaozhi.vad.segment-ms:60000}")
    private int segmentMs;

    // 一次说话的总时长天花板，到点强制收句回答；之后的音频丢弃到用户停顿为止，不然一条用户消息会撑爆上下文
    @Value("${xiaozhi.vad.max-speech-ms:300000}")
    private int maxSpeechMs;

    private static final int SILENCE_FRAME_THRESHOLD = 2;
    private static final int VAD_SAMPLE_SIZE = AudioUtils.BUFFER_SIZE;
    private static final int VAD_CONTEXT_SIZE = SileroVadModel.CONTEXT_SIZE;
    // 连续静音帧数阈值，超过时重置GRU状态，防止长时间静音后GRU深度收敛（30帧 ≈ 约2秒）
    private static final int SILENCE_RESET_FRAMES = 30;
    // 16kHz、16bit、单声道
    private static final int PCM_BYTES_PER_MS = 32;

    // 角色未配置时的 VAD 阈值默认值
    private static final float DEFAULT_SPEECH_THRESHOLD = 0.4f;
    private static final float DEFAULT_SILENCE_THRESHOLD = 0.3f;
    private static final float DEFAULT_ENERGY_THRESHOLD = 0.001f;
    private static final int DEFAULT_SILENCE_TIMEOUT_MS = 800;

    /** 一次会话内使用的角色 VAD 阈值 */
    private record RoleThresholds(float speech, float silence, float energy, int silenceMs) {
        private static final RoleThresholds DEFAULTS = new RoleThresholds(
                DEFAULT_SPEECH_THRESHOLD, DEFAULT_SILENCE_THRESHOLD,
                DEFAULT_ENERGY_THRESHOLD, DEFAULT_SILENCE_TIMEOUT_MS);
    }

    @Autowired
    private SileroVadModel vadModel;

    @Autowired
    private RoleService roleService;

    @Autowired
    private SessionManager sessionManager;

    @Resource
    private AecService aecService;

    @PreDestroy
    public void cleanup() {
        log.info("VAD服务资源已释放");
        states.clear();
    }

    private class VadState {
        // 是否由服务端 VAD 自动断句。manual 模式为 false
        private boolean autoSegment = true;

        // 角色阈值快照。只在 initSession 与角色变更时整体替换，读写都在 session 锁内
        private RoleThresholds thresholds = RoleThresholds.DEFAULTS;

        private boolean speaking = false;
        private long silenceTime = 0;

        private int consecutiveSilenceFrames = 0;
        private int consecutiveSpeechFrames = 0;

        // 静音期间累计帧数，用于SPEECH_END时按比例移除静音帧
        private int silenceFrameCount = 0;

        // 本次说话开口后已喂入的语音总时长
        private int spokenMs = 0;
        // 当前识别段已喂入的语音时长，换流时清零
        private int segmentSpokenMs = 0;
        // 本次说话已因达到总时长天花板被强制收句，后续音频丢弃到用户停顿（manual 为 listen/stop）为止
        private boolean truncated = false;

        private float[][][] sileroState = new float[2][1][128];
        // 跨帧样本拼接缓冲
        private float[] sampleCarryOver = new float[0];
        private float[] vadContext = new float[VAD_CONTEXT_SIZE];

        private final LinkedList<byte[]> preBuffer = new LinkedList<>();
        private int preBufferSize = 0;
        private final int maxPreBufferSize;

        private final List<byte[]> pcmData = new ArrayList<>();
        private int pcmDataSize = 0;
        private final int maxPcmSize;

        // 每个 session 复用同一个 OpusProcessor，避免每帧重新创建 native 编解码器
        private final OpusProcessor opusProcessor = new OpusProcessor();

        public VadState() {
            this.maxPreBufferSize = preBufferMs * PCM_BYTES_PER_MS;
            // 缓冲按识别段算：换流时整段交给识别侧，缓冲从零开始装下一段
            this.maxPcmSize = (segmentMs + preBufferMs) * PCM_BYTES_PER_MS;
        }

        public boolean isSpeaking() { return speaking; }

        public void setSpeaking(boolean speaking) {
            this.speaking = speaking;
            if (speaking) {
                silenceTime = 0;
            } else if (silenceTime == 0) {
                silenceTime = System.currentTimeMillis();
            }
        }

        public int getSilenceDuration() {
            if (silenceTime == 0) return 0;
            return (int) (System.currentTimeMillis() - silenceTime);
        }

        public int getConsecutiveSilenceFrames() { return consecutiveSilenceFrames; }
        public int getConsecutiveSpeechFrames() { return consecutiveSpeechFrames; }

        public void updateSilence(boolean isSilent) {
            if (isSilent) {
                consecutiveSilenceFrames++;
                consecutiveSpeechFrames = 0;
                if (silenceTime == 0) {
                    silenceTime = System.currentTimeMillis();
                }
            } else {
                consecutiveSpeechFrames++;
                if (consecutiveSpeechFrames >= SILENCE_FRAME_THRESHOLD) {
                    consecutiveSilenceFrames = 0;
                    silenceTime = 0;
                    silenceFrameCount = 0;
                }
            }
        }

        public void incrementSilenceFrameCount() { silenceFrameCount++; }
        public int getSilenceFrameCount() { return silenceFrameCount; }
        public void resetSilenceFrameCount() { silenceFrameCount = 0; }

        public void addToPreBuffer(byte[] data) {
            if (speaking) return;
            preBuffer.add(data.clone());
            preBufferSize += data.length;
            while (preBufferSize > maxPreBufferSize && !preBuffer.isEmpty()) {
                byte[] removed = preBuffer.removeFirst();
                preBufferSize -= removed.length;
            }
        }

        public byte[] drainPreBuffer() {
            if (preBuffer.isEmpty()) return new byte[0];
            byte[] result = new byte[preBufferSize];
            int offset = 0;
            for (byte[] chunk : preBuffer) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            preBuffer.clear();
            preBufferSize = 0;
            return result;
        }

        public void addPcm(byte[] pcm) {
            if (pcm == null || pcm.length == 0) return;
            // 已达上限：异常场景（持续噪音、迟迟不触发静音结束）不再继续累积，防止内存无限增长
            if (pcmDataSize >= maxPcmSize) return;
            pcmData.add(pcm.clone());
            pcmDataSize += pcm.length;
        }

        /** 按静音尾帧比例裁剪时从末尾丢帧，与 addPcm 共用同一个大小计数器 */
        public void removeLastPcmFrame() {
            if (pcmData.isEmpty()) return;
            byte[] removed = pcmData.remove(pcmData.size() - 1);
            pcmDataSize -= removed.length;
        }

        public List<byte[]> getPcmData() { return new ArrayList<>(pcmData); }

        public void clearPcm() {
            pcmData.clear();
            pcmDataSize = 0;
        }

        public void reset() {
            speaking = false;
            silenceTime = 0;
            consecutiveSilenceFrames = 0;
            consecutiveSpeechFrames = 0;
            silenceFrameCount = 0;
            sileroState = new float[2][1][128];
            sampleCarryOver = new float[0];
            vadContext = new float[VAD_CONTEXT_SIZE];
            preBuffer.clear();
            preBufferSize = 0;
            spokenMs = 0;
            segmentSpokenMs = 0;
            truncated = false;
            clearPcm();
        }
    }

    public void initSession(String sessionId) {
        initSession(sessionId, true);
    }

    /**
     * @param autoSegment 是否由服务端 VAD 断句。manual 传 false，仍做解码与 AEC，只是不跑断句状态机
     */
    public void initSession(String sessionId, boolean autoSegment) {
        // 阈值读放在锁外，不占着 session 锁等缓存/数据库
        RoleThresholds thresholds = readRoleThresholds(sessionId);
        // 新建的 VadState 各字段本就是 reset() 之后的值，复用同一个方法不用区分新建/复用两条路径
        VadState state = states.computeIfAbsent(sessionId, k -> new VadState());
        synchronized (state) {
            state.reset();
            state.autoSegment = autoSegment;
            state.thresholds = thresholds;
        }
        log.info("VAD会话已初始化: {}, 自动断句: {}", sessionId, autoSegment);
    }

    /**
     * 取一次角色阈值，音频帧处理直接读快照，不再逐帧查缓存。角色没配或读不到时用默认值。
     */
    private RoleThresholds readRoleThresholds(String sessionId) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        DeviceBO device = chatSession != null ? chatSession.getDevice() : null;
        if (device == null || device.getRoleId() == null) {
            return RoleThresholds.DEFAULTS;
        }
        RoleBO role = roleService.getBO(device.getRoleId());
        if (role == null) {
            return RoleThresholds.DEFAULTS;
        }
        return new RoleThresholds(
                Optional.ofNullable(role.getVadSpeechTh()).orElse(DEFAULT_SPEECH_THRESHOLD),
                Optional.ofNullable(role.getVadSilenceTh()).orElse(DEFAULT_SILENCE_THRESHOLD),
                Optional.ofNullable(role.getVadEnergyTh()).orElse(DEFAULT_ENERGY_THRESHOLD),
                Optional.ofNullable(role.getVadSilenceMs()).orElse(DEFAULT_SILENCE_TIMEOUT_MS));
    }

    /**
     * 重新读取指定会话的角色阈值快照，供角色变更广播（跨实例）调用；
     * 不调用时阈值在下一次 listen/start 的 initSession 里刷新。会话未初始化 VAD 时什么都不做。
     */
    public void refreshRoleThresholds(String sessionId) {
        VadState state = states.get(sessionId);
        if (state == null) {
            return;
        }
        RoleThresholds thresholds = readRoleThresholds(sessionId);
        synchronized (state) {
            // 读阈值期间可能被 resetSession 摘除，摘除后不再对同一个 state 对象生效
            if (states.get(sessionId) == state) {
                state.thresholds = thresholds;
            }
        }
    }

    public boolean isSessionInitialized(String sessionId) {
        return states.containsKey(sessionId);
    }

    public VadResult processAudio(String sessionId, byte[] opusData) {
        return processAudio(sessionId, opusData, 0);
    }

    /**
     * @param echoTimestamp 设备回显的下行帧时间戳，0 表示无；透传给 AEC 做参考对齐
     */
    public VadResult processAudio(String sessionId, byte[] opusData, long echoTimestamp) {
        VadState state = states.get(sessionId);
        if (state == null) return null;

        synchronized (state) {
            try {
                // 加锁等待期间可能被 resetSession 摘除，摘除后不再对同一个 state 对象继续处理，避免残帧复活已关闭的会话
                if (states.get(sessionId) != state) {
                    return null;
                }

                byte[] pcmData;
                try {
                    pcmData = state.opusProcessor.opusToPcm(opusData);
                    if (pcmData == null || pcmData.length == 0) {
                        return new VadResult(VadStatus.NO_SPEECH, null);
                    }
                } catch (Exception e) {
                    log.error("Opus解码失败: {}", e.getMessage());
                    return new VadResult(VadStatus.ERROR, null);
                }

                // AEC 处理：消除麦克风中的扬声器回声。仅对声明了 features.aec 的设备生效
                if (aecService != null) {
                    pcmData = aecService.process(sessionId, pcmData, echoTimestamp);
                }

                if (state.isSpeaking()) {
                    int frameMs = pcmData.length / PCM_BYTES_PER_MS;
                    state.spokenMs += frameMs;
                    state.segmentSpokenMs += frameMs;
                    // 说到总天花板即强制收句回答
                    if (state.spokenMs >= maxSpeechMs) {
                        state.setSpeaking(false);
                        state.resetSilenceFrameCount();
                        state.truncated = true;
                        log.info("单次说话达到上限 {}ms，强制收句，后续音频丢弃到停顿为止 - SessionId: {}",
                                maxSpeechMs, sessionId);
                        return new VadResult(VadStatus.SPEECH_END, pcmData);
                    }
                    // 说到单段上限即换识别流：整段 PCM 交给识别侧做重放与落盘，缓冲从这一帧重新开始
                    if (state.segmentSpokenMs >= segmentMs) {
                        List<byte[]> segmentPcm = state.getPcmData();
                        state.clearPcm();
                        state.addPcm(pcmData);
                        state.segmentSpokenMs = 0;
                        log.info("单段语音达到 {}ms，切换识别流继续 - SessionId: {}", segmentMs, sessionId);
                        return new VadResult(VadStatus.SPEECH_ROTATE, pcmData, segmentPcm);
                    }
                }

                // manual 模式跳过 Silero：首帧起流，其余持续喂流，收句由 listen/stop 触发
                if (!state.autoSegment) {
                    if (state.truncated) {
                        return new VadResult(VadStatus.NO_SPEECH, null);
                    }
                    if (!state.isSpeaking()) {
                        state.clearPcm();
                        state.setSpeaking(true);
                        state.spokenMs = 0;
                        state.segmentSpokenMs = 0;
                        state.addPcm(pcmData);
                        return new VadResult(VadStatus.SPEECH_START, pcmData);
                    }
                    state.addPcm(pcmData);
                    return new VadResult(VadStatus.SPEECH_CONTINUE, pcmData);
                }

                float[] samples = AudioUtils.pcm16ToFloats(pcmData);
                float energy = calcEnergy(samples);

                float speechProb = Math.min(1.0f, detectSpeech(state, samples));

                state.addToPreBuffer(pcmData);

                boolean hasEnergy = energy > state.thresholds.energy();

                // 播放和静听使用完全相同的判断逻辑
                boolean isSpeech = speechProb > state.thresholds.speech() && hasEnergy;
                boolean isSilence = speechProb < state.thresholds.silence() || !hasEnergy;

                state.updateSilence(isSilence);

                // 连续静音超过阈值时自动重置GRU状态，防止GRU深度收敛，导致在长时间静音状态下VAD无法被拉起
                if (state.getConsecutiveSilenceFrames() >= SILENCE_RESET_FRAMES) {
                    state.sileroState = new float[2][1][128];
                    state.sampleCarryOver = new float[0];
                    state.vadContext = new float[VAD_CONTEXT_SIZE];
                    state.consecutiveSilenceFrames = 0;
                }

                boolean speechStartAllowed = state.getConsecutiveSpeechFrames() >= 2;

                // 被截断的那句剩下的部分不再起新一轮，等用户停顿够久才恢复检测
                if (state.truncated) {
                    if (isSilence && state.getSilenceDuration() > state.thresholds.silenceMs()) {
                        state.truncated = false;
                    }
                    return new VadResult(VadStatus.NO_SPEECH, null);
                }

                // log.debug("VAD[{}] prob:{} nrg:{} sil:{}ms({}) {}{}",
                //         sessionId,
                //         String.format("%.3f", speechProb),
                //         String.format("%.4f", energy),
                //         state.getSilenceDuration(), state.getConsecutiveSilenceFrames(),
                //         isSilence ? "sil" : "SPK",
                //         hasEnergy ? "+E" : "");

                if (!state.isSpeaking() && isSpeech && speechStartAllowed) {
                    state.clearPcm();
                    state.setSpeaking(true);
                    state.spokenMs = 0;
                    state.segmentSpokenMs = 0;
                    state.resetSilenceFrameCount();

                    log.debug("检测到语音开始 - SessionId: {}, 概率: {}, 能量: {}, 阈值: {}",
                            sessionId, String.format("%.4f", speechProb),
                            String.format("%.6f", energy), String.format("%.4f", state.thresholds.speech()));

                    byte[] preBufferData = state.drainPreBuffer();
                    byte[] result = preBufferData.length > 0 ? preBufferData : pcmData;
                    state.addPcm(result);
                    return new VadResult(VadStatus.SPEECH_START, result);

                } else if (state.isSpeaking() && isSilence) {
                    int silenceDuration = state.getSilenceDuration();
                    if (silenceDuration > state.thresholds.silenceMs()) {
                        state.setSpeaking(false);

                        int silenceToRemoveMs = silenceDuration - tailKeepMs;
                        if (silenceToRemoveMs > 0) {
                            int totalSilenceFrames = state.getSilenceFrameCount();
                            if (totalSilenceFrames > 0) {
                                int framesToRemove = Math.min(
                                    (int) Math.ceil((double) totalSilenceFrames * silenceToRemoveMs / silenceDuration),
                                    totalSilenceFrames
                                );
                                for (int i = 0; i < framesToRemove; i++) {
                                    state.removeLastPcmFrame();
                                }
                            }
                        }
                        log.debug("语音结束: {}, 静音: {}ms", sessionId, silenceDuration);

                        state.resetSilenceFrameCount();

                        return new VadResult(VadStatus.SPEECH_END, pcmData);
                    } else {
                        state.addPcm(pcmData);
                        state.incrementSilenceFrameCount();
                        return new VadResult(VadStatus.SPEECH_CONTINUE, pcmData);
                    }
                } else if (state.isSpeaking()) {
                    state.addPcm(pcmData);
                    state.resetSilenceFrameCount();
                    return new VadResult(VadStatus.SPEECH_CONTINUE, pcmData);
                } else {
                    return new VadResult(VadStatus.NO_SPEECH, null);
                }
            } catch (Exception e) {
                log.error("处理音频失败: {}, 错误: {}", sessionId, e.getMessage(), e);
                return new VadResult(VadStatus.ERROR, null);
            }
        }
    }

    /**
     * 将上一帧剩余样本（sampleCarryOver）与本帧拼接，按VAD_SAMPLE_SIZE逐块推理。
     * 始终使用有状态推理，通过连续静音定期重置GRU防止深度收敛。
     */
    private float detectSpeech(VadState state, float[] samples) {
        if (vadModel == null || samples == null || samples.length == 0) {
            log.warn("VAD模型为空或样本为空");
            return 0.0f;
        }
        try {
            float[] all;
            if (state.sampleCarryOver.length > 0) {
                all = new float[state.sampleCarryOver.length + samples.length];
                System.arraycopy(state.sampleCarryOver, 0, all, 0, state.sampleCarryOver.length);
                System.arraycopy(samples, 0, all, state.sampleCarryOver.length, samples.length);
            } else {
                all = samples;
            }

            float maxProb = 0.0f;
            int offset = 0;
            while (offset + VAD_SAMPLE_SIZE <= all.length) {
                float[] chunk = Arrays.copyOfRange(all, offset, offset + VAD_SAMPLE_SIZE);
                InferenceResult r = vadModel.infer(chunk, state.vadContext, state.sileroState);
                state.vadContext = Arrays.copyOfRange(chunk, chunk.length - VAD_CONTEXT_SIZE, chunk.length);
                state.sileroState = r.state;
                maxProb = Math.max(maxProb, r.probability);
                offset += VAD_SAMPLE_SIZE;
            }

            int remaining = all.length - offset;
            state.sampleCarryOver = remaining > 0 ? Arrays.copyOfRange(all, offset, all.length) : new float[0];

            return maxProb;
        } catch (Exception e) {
            log.error("VAD推断失败: {}", e.getMessage());
            return 0.0f;
        }
    }

    private float calcEnergy(float[] samples) {
        float sum = 0;
        for (float sample : samples) sum += Math.abs(sample);
        return sum / samples.length;
    }

    /**
     * TTS播放结束时重置VAD隐状态，清除TTS期间麦克风拾音对GRU的污染。
     */
    @EventListener
    public void onTtsPlaybackEnd(TtsPlaybackCompletedEvent event) {
        resetVadModelState(event.getSessionId());
    }

    public void resetVadModelState(String sessionId) {
        VadState state = states.get(sessionId);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (states.get(sessionId) == state) {
                state.sileroState = new float[2][1][128];
                state.sampleCarryOver = new float[0];
                state.vadContext = new float[VAD_CONTEXT_SIZE];
            }
        }
    }

    public void resetSession(String sessionId) {
        VadState state = states.get(sessionId);
        if (state == null) {
            return;
        }
        synchronized (state) {
            // remove 放在锁内跟其它所有加锁点共用同一个 state 对象做互斥，不会出现摘除和处理各拿到不同锁的情况
            if (states.get(sessionId) == state) {
                state.reset();
                states.remove(sessionId);
            }
        }
    }

    /**
     * 收句：标记本轮语音结束，供 manual 模式在 listen/stop 时调用。检查与清除是原子的。
     *
     * @return 本轮是否确有语音在进行中
     */
    public boolean finishSegment(String sessionId) {
        VadState state = states.get(sessionId);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            if (states.get(sessionId) != state) {
                return false;
            }
            // listen/stop 之后客户端不再送音频，被截断那轮的丢弃状态到此结束
            state.truncated = false;
            if (!state.isSpeaking()) {
                return false;
            }
            state.setSpeaking(false);
            return true;
        }
    }

    public List<byte[]> getPcmData(String sessionId) {
        VadState state = states.get(sessionId);
        if (state == null) {
            return new ArrayList<>();
        }
        synchronized (state) {
            return state.getPcmData();
        }
    }

    public enum VadStatus {
        NO_SPEECH, SPEECH_START, SPEECH_CONTINUE, SPEECH_END, ERROR,
        /** 当前段到时长上限：识别侧终结当前流、起新流继续，不收句 */
        SPEECH_ROTATE
    }

    public static class VadResult {
        private final VadStatus status;
        private final byte[] data;
        private final List<byte[]> segmentPcm;

        public VadResult(VadStatus status, byte[] data) {
            this(status, data, null);
        }

        /** @param segmentPcm SPEECH_ROTATE 时交出的上一段整段 PCM，其余状态为 null */
        public VadResult(VadStatus status, byte[] data, List<byte[]> segmentPcm) {
            this.status = status;
            this.data = data;
            this.segmentPcm = segmentPcm;
        }

        public VadStatus getStatus() { return status; }
        public byte[] getProcessedData() { return data; }
        public List<byte[]> getSegmentPcm() { return segmentPcm; }
    }
}

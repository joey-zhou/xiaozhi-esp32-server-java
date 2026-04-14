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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.xiaozhi.event.TtsPlaybackCompletedEvent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音活动检测服务
 */
@Service
public class VadService {
    private static final Logger logger = LoggerFactory.getLogger(VadService.class);

    private final ConcurrentHashMap<String, VadState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Value("${vad.prebuffer.ms:500}")
    private int preBufferMs;

    @Value("${vad.tail.keep.ms:300}")
    private int tailKeepMs;

    private static final int SILENCE_FRAME_THRESHOLD = 2;
    private static final int VAD_SAMPLE_SIZE = AudioUtils.BUFFER_SIZE;
    // 连续静音帧数阈值，超过时重置GRU状态，防止长时间静音后GRU深度收敛（30帧 ≈ 约2秒）
    private static final int SILENCE_RESET_FRAMES = 30;

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
        logger.info("VAD服务资源已释放");
        states.clear();
        locks.clear();
    }

    private class VadState {
        private boolean speaking = false;
        private long silenceTime = 0;

        private int consecutiveSilenceFrames = 0;
        private int consecutiveSpeechFrames = 0;

        // 静音期间累计帧数，用于SPEECH_END时按比例移除静音帧
        private int silenceFrameCount = 0;

        private final List<Float> originalProbs = new ArrayList<>();
        private float[][][] sileroState = new float[2][1][128];
        // 跨帧样本拼接缓冲
        private float[] sampleCarryOver = new float[0];

        private final LinkedList<byte[]> preBuffer = new LinkedList<>();
        private int preBufferSize = 0;
        private final int maxPreBufferSize;

        private final List<byte[]> pcmData = new ArrayList<>();

        // 每个 session 复用同一个 OpusProcessor，避免每帧重新创建 native 编解码器
        private final OpusProcessor opusProcessor = new OpusProcessor();

        public VadState() {
            this.maxPreBufferSize = preBufferMs * 32; // 16kHz, 16bit, mono = 32 bytes/ms
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

        public void addOriginalProb(float prob) {
            originalProbs.add(prob);
            if (originalProbs.size() > 10) originalProbs.remove(0);
        }

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
            if (pcm != null && pcm.length > 0) pcmData.add(pcm.clone());
        }

        public List<byte[]> getPcmData() { return new ArrayList<>(pcmData); }

        public void reset() {
            speaking = false;
            silenceTime = 0;
            consecutiveSilenceFrames = 0;
            consecutiveSpeechFrames = 0;
            silenceFrameCount = 0;
            originalProbs.clear();
            sileroState = new float[2][1][128];
            sampleCarryOver = new float[0];
            preBuffer.clear();
            preBufferSize = 0;
            pcmData.clear();
        }
    }

    public void initSession(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            VadState state = states.get(sessionId);
            if (state == null) {
                states.put(sessionId, new VadState());
            } else {
                state.reset();
            }
            logger.info("VAD会话已初始化: {}", sessionId);
        }
    }

    public boolean isSessionInitialized(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            return states.containsKey(sessionId);
        }
    }

    private Object getLock(String sessionId) {
        return locks.computeIfAbsent(sessionId, k -> new Object());
    }

    public VadResult processAudio(String sessionId, byte[] opusData) {
        if (!isSessionInitialized(sessionId)) return null;

        Object lock = getLock(sessionId);

        ChatSession chatSession = sessionManager.getSession(sessionId);
        DeviceBO device = chatSession != null ? chatSession.getDevice() : null;
        float speechThreshold = 0.4f;
        float silenceThreshold = 0.3f;
        float energyThreshold = 0.001f;
        int silenceTimeoutMs = 800;

        if (device != null && device.getRoleId() != null) {
            RoleBO role = roleService.getBO(device.getRoleId());
            if (role != null) {
                speechThreshold = Optional.ofNullable(role.getVadSpeechTh()).orElse(speechThreshold);
                silenceThreshold = Optional.ofNullable(role.getVadSilenceTh()).orElse(silenceThreshold);
                energyThreshold = Optional.ofNullable(role.getVadEnergyTh()).orElse(energyThreshold);
                silenceTimeoutMs = Optional.ofNullable(role.getVadSilenceMs()).orElse(silenceTimeoutMs);
            }
        }

        synchronized (lock) {
            try {
                VadState state = states.computeIfAbsent(sessionId, k -> new VadState());

                byte[] pcmData;
                try {
                    pcmData = state.opusProcessor.opusToPcm(opusData);
                    if (pcmData == null || pcmData.length == 0) {
                        return new VadResult(VadStatus.NO_SPEECH, null);
                    }
                } catch (Exception e) {
                    logger.error("Opus解码失败: {}", e.getMessage());
                    return new VadResult(VadStatus.ERROR, null);
                }

                // AEC 处理：消除麦克风中的扬声器回声
                if (aecService != null && aecService.isEnabled()) {
                    pcmData = aecService.process(sessionId, pcmData);
                }

                float[] samples = bytesToFloats(pcmData);
                float energy = calcEnergy(samples);

                float speechProb = Math.min(1.0f, detectSpeech(state, samples));

                state.addOriginalProb(speechProb);
                state.addToPreBuffer(pcmData);

                boolean hasEnergy = energy > energyThreshold;

                // 播放和静听使用完全相同的判断逻辑
                boolean isSpeech = speechProb > speechThreshold && hasEnergy;
                boolean isSilence = speechProb < silenceThreshold || !hasEnergy;

                state.updateSilence(isSilence);

                // 连续静音超过阈值时自动重置GRU状态，防止GRU深度收敛，导致在长时间静音状态下VAD无法被拉起
                if (state.getConsecutiveSilenceFrames() >= SILENCE_RESET_FRAMES) {
                    state.sileroState = new float[2][1][128];
                    state.sampleCarryOver = new float[0];
                    state.originalProbs.clear();
                    state.consecutiveSilenceFrames = 0;
                }

                boolean speechStartAllowed = state.getConsecutiveSpeechFrames() >= 2;

                // logger.debug("VAD[{}] prob:{} nrg:{} sil:{}ms({}) {}{}",
                //         sessionId,
                //         String.format("%.3f", speechProb),
                //         String.format("%.4f", energy),
                //         state.getSilenceDuration(), state.getConsecutiveSilenceFrames(),
                //         isSilence ? "sil" : "SPK",
                //         hasEnergy ? "+E" : "");

                if (!state.isSpeaking() && isSpeech && speechStartAllowed) {
                    state.pcmData.clear();
                    state.setSpeaking(true);
                    state.resetSilenceFrameCount();

                    logger.debug("检测到语音开始 - SessionId: {}, 概率: {}, 能量: {}, 阈值: {}",
                            sessionId, String.format("%.4f", speechProb),
                            String.format("%.6f", energy), String.format("%.4f", speechThreshold));

                    byte[] preBufferData = state.drainPreBuffer();
                    byte[] result = preBufferData.length > 0 ? preBufferData : pcmData;
                    state.addPcm(result);
                    return new VadResult(VadStatus.SPEECH_START, result);

                } else if (state.isSpeaking() && isSilence) {
                    int silenceDuration = state.getSilenceDuration();
                    if (silenceDuration > silenceTimeoutMs) {
                        state.setSpeaking(false);

                        int silenceToRemoveMs = silenceDuration - tailKeepMs;
                        if (silenceToRemoveMs > 0) {
                            int totalSilenceFrames = state.getSilenceFrameCount();
                            if (totalSilenceFrames > 0) {
                                int framesToRemove = Math.min(
                                    (int) Math.ceil((double) totalSilenceFrames * silenceToRemoveMs / silenceDuration),
                                    totalSilenceFrames
                                );
                                for (int i = 0; i < framesToRemove && !state.pcmData.isEmpty(); i++) {
                                    state.pcmData.remove(state.pcmData.size() - 1);
                                }
                            }
                        }
                        logger.debug("语音结束: {}, 静音: {}ms", sessionId, silenceDuration);

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
                logger.error("处理音频失败: {}, 错误: {}", sessionId, e.getMessage(), e);
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
            logger.warn("VAD模型为空或样本为空");
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
                InferenceResult r = vadModel.infer(chunk, state.sileroState);
                state.sileroState = r.state;
                maxProb = Math.max(maxProb, r.probability);
                offset += VAD_SAMPLE_SIZE;
            }

            int remaining = all.length - offset;
            state.sampleCarryOver = remaining > 0 ? Arrays.copyOfRange(all, offset, all.length) : new float[0];

            return maxProb;
        } catch (Exception e) {
            logger.error("VAD推断失败: {}", e.getMessage());
            return 0.0f;
        }
    }

    private float[] bytesToFloats(byte[] pcmData) {
        int sampleCount = pcmData.length / 2;
        float[] samples = new float[sampleCount];
        ByteBuffer buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = buffer.getShort() / 32768.0f;
        }
        return samples;
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
        Object lock = getLock(sessionId);
        synchronized (lock) {
            VadState state = states.get(sessionId);
            if (state != null) {
                state.sileroState = new float[2][1][128];
                state.sampleCarryOver = new float[0];
                state.originalProbs.clear();
            }
        }
    }

    public void resetSession(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            VadState state = states.get(sessionId);
            if (state != null) state.reset();
            states.remove(sessionId);
            locks.remove(sessionId);
            logger.info("VAD会话已重置: {}", sessionId);
        }
    }

    public List<byte[]> getPcmData(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            VadState state = states.get(sessionId);
            return state != null ? state.getPcmData() : new ArrayList<>();
        }
    }

    public enum VadStatus {
        NO_SPEECH, SPEECH_START, SPEECH_CONTINUE, SPEECH_END, ERROR
    }

    public static class VadResult {
        private final VadStatus status;
        private final byte[] data;

        public VadResult(VadStatus status, byte[] data) {
            this.status = status;
            this.data = data;
        }

        public VadStatus getStatus() { return status; }
        public byte[] getProcessedData() { return data; }
    }
}

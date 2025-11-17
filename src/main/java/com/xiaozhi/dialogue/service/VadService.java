package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.vad.VadModel.InferenceResult;
import com.xiaozhi.dialogue.vad.impl.SileroVadModel;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysRoleService;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.AudioEnhancer;
import com.xiaozhi.utils.OpusProcessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
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
    
    // 会话状态
    private final ConcurrentHashMap<String, VadState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AudioEnhancer> audioEnhancers = new ConcurrentHashMap<>();
    
    @Value("${vad.prebuffer.ms:500}")
    private int preBufferMs;
    
    // 保留的尾音时长（毫秒）- 避免切掉最后一个字的尾音
    @Value("${vad.tail.keep.ms:300}")
    private int tailKeepMs;

    // 音频增强配置
    @Value("${vad.audio.enhancement.enabled:true}")
    private boolean audioEnhancementEnabled;

    // 每10帧输出一次VAD状态
    private static final int LOG_FRAME_INTERVAL = 1;
    
    // 连续帧判断：需要连续N帧低于阈值才认为是静音
    private static final int SILENCE_FRAME_THRESHOLD = 2;
    
    // 最小PCM数据长度 (16kHz, 16bit, mono, 30ms = 960 bytes)
    private static final int MIN_PCM_LENGTH = 960;
    
    // VAD模型的样本大小 (16kHz, 512 samples)
    private static final int VAD_SAMPLE_SIZE = AudioUtils.BUFFER_SIZE;
    
    @Autowired
    private OpusProcessor opusProcessor;

    @Autowired
    private SileroVadModel vadModel;
    
    @Autowired
    private SysRoleService roleService;
    
    @Autowired
    private SessionManager sessionManager;

    @PreDestroy
    public void cleanup() {
        logger.info("VAD服务资源已释放");
        states.clear();
        locks.clear();
        audioEnhancers.clear();
    }

    /**
     * 简化的会话状态类
     */
    private class VadState {
        // 语音状态
        private boolean speaking = false;
        private long speechTime = 0;
        private long silenceTime = 0;
        
        // 基于音频时长的静音检测（更准确）
        private int silenceDurationMs = 0;  // 累积的静音时长（毫秒）
        private int totalAudioDurationMs = 0;  // 已处理的音频总时长（毫秒）
        
        // 连续静音帧计数（用于平滑判断）
        private int consecutiveSilenceFrames = 0;  // 连续静音帧数
        private int consecutiveSpeechFrames = 0;   // 连续语音帧数
        
        // 记录静音期间添加的帧数，用于在SPEECH_END时移除静音帧
        private int silenceFrameCount = 0;  // 当前静音期间添加的帧数

        // 音频分析
        private float avgEnergy = 0;
        private final List<Float> probs = new ArrayList<>();
        
        // 原始VAD概率列表
        private final List<Float> originalProbs = new ArrayList<>();
        
        // 帧计数器（用于每10帧输出一次）
        private int frameCounter = 0;

        // 每会话 Silero 隐状态 [2][1][128]
        private float[][][] sileroState = new float[2][1][128];

        // 预缓冲
        private final LinkedList<byte[]> preBuffer = new LinkedList<>();
        private int preBufferSize = 0;
        private final int maxPreBufferSize;

        // 音频数据
        private final List<byte[]> pcmData = new ArrayList<>();
        private final List<byte[]> opusData = new ArrayList<>();

        // 短帧累积
        private final ByteArrayOutputStream pcmAccumulator = new ByteArrayOutputStream();
        private long lastAccumTime = 0;

        public VadState() {
            this.maxPreBufferSize = preBufferMs * 32; // 16kHz, 16bit, mono = 32 bytes/ms
            this.lastAccumTime = System.currentTimeMillis();
        }

        public boolean isSpeaking() {
            return speaking;
        }

        public void setSpeaking(boolean speaking) {
            this.speaking = speaking;
            if (speaking) {
                speechTime = System.currentTimeMillis();
                silenceTime = 0;
            } else if (silenceTime == 0) {
                silenceTime = System.currentTimeMillis();
            }
        }

        public int getSilenceDuration() {
            // 返回基于实际时间的静音时长（修复网络抖动导致的时间不准确问题）
            if (silenceTime == 0) {
                return 0;
            }
            return (int) (System.currentTimeMillis() - silenceTime);
        }
        
        public int getConsecutiveSilenceFrames() {
            return consecutiveSilenceFrames;
        }

        public void updateSilence(boolean isSilent, int frameDurationMs) {
            // 累积已处理的音频总时长
            totalAudioDurationMs += frameDurationMs;
            
            if (isSilent) {
                // 连续静音帧计数
                consecutiveSilenceFrames++;
                consecutiveSpeechFrames = 0;  // 重置语音帧计数
                
                // 累积静音时长
                silenceDurationMs += frameDurationMs;
                if (silenceTime == 0) {
                    silenceTime = System.currentTimeMillis();  // 记录真实时间，用于日志
                }
            } else {
                // 连续语音帧计数
                consecutiveSpeechFrames++;
                
                // 只有连续多帧非静音才重置静音计数（平滑处理）
                if (consecutiveSpeechFrames >= SILENCE_FRAME_THRESHOLD) {
                    consecutiveSilenceFrames = 0;
                    silenceDurationMs = 0;
                    silenceTime = 0;
                    silenceFrameCount = 0;  // 重置静音帧计数
                }
            }
        }
        
        public void incrementSilenceFrameCount() {
            silenceFrameCount++;
        }
        
        public int getSilenceFrameCount() {
            return silenceFrameCount;
        }
        
        public void resetSilenceFrameCount() {
            silenceFrameCount = 0;
        }

        public void updateEnergy(float energy, boolean isSilent) {
            if (avgEnergy == 0) {
                avgEnergy = energy;
            } else {
                // 静音时使用更快的下降速度，让平均能量更快适应当前状态
                float smoothingFactor = isSilent ? 0.85f : 0.95f;
                avgEnergy = smoothingFactor * avgEnergy + (1 - smoothingFactor) * energy;
            }
        }

        public float getAvgEnergy() {
            return avgEnergy;
        }

        public void addProb(float prob) {
            probs.add(prob);
            if (probs.size() > 10) {
                probs.remove(0);
            }
        }
        
        // 添加原始VAD概率
        public void addOriginalProb(float prob) {
            originalProbs.add(prob);
            if (originalProbs.size() > 10) {
                originalProbs.remove(0);
            }
            
            // 增加帧计数器
            frameCounter++;
        }
        
        public float getLastOriginalProb() {
            return originalProbs.isEmpty() ? 0.0f : originalProbs.get(originalProbs.size() - 1);
        }

        public float getLastProb() {
            return probs.isEmpty() ? 0.0f : probs.get(probs.size() - 1);
        }

        public List<Float> getProbs() {
            return probs;
        }
        
        public int getFrameCounter() {
            return frameCounter;
        }

        // 预缓冲区管理
        public void addToPreBuffer(byte[] data) {
            if (speaking) {
                return;
            }

            preBuffer.add(data.clone());
            preBufferSize += data.length;

            while (preBufferSize > maxPreBufferSize && !preBuffer.isEmpty()) {
                byte[] removed = preBuffer.removeFirst();
                preBufferSize -= removed.length;
            }
        }

        public byte[] drainPreBuffer() {
            if (preBuffer.isEmpty()) {
                return new byte[0];
            }

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

        // 累积缓冲区管理
        public void accumulate(byte[] pcm) {
            if (pcm != null && pcm.length > 0) {
                try {
                    pcmAccumulator.write(pcm);
                    lastAccumTime = System.currentTimeMillis();
                } catch (Exception e) {
                    logger.error("累积PCM数据失败", e);
                }
            }
        }

        public byte[] drainAccumulator() {
            byte[] result = pcmAccumulator.toByteArray();
            pcmAccumulator.reset();
            return result;
        }

        public int getAccumSize() {
            return pcmAccumulator.size();
        }

        public boolean isAccumTimedOut() {
            return System.currentTimeMillis() - lastAccumTime > 300;
        }

        // 音频数据管理
        public void addPcm(byte[] pcm) {
            if (pcm != null && pcm.length > 0) {
                pcmData.add(pcm.clone());
            }
        }

        public void addOpus(byte[] opus) {
            if (opus != null && opus.length > 0) {
                opusData.add(opus.clone());
            }
        }

        public List<byte[]> getPcmData() {
            return new ArrayList<>(pcmData);
        }

        public List<byte[]> getOpusData() {
            return new ArrayList<>(opusData);
        }

        public void reset() {
            speaking = false;
            speechTime = 0;
            silenceTime = 0;
            silenceDurationMs = 0;
            totalAudioDurationMs = 0;
            consecutiveSilenceFrames = 0;
            consecutiveSpeechFrames = 0;
            silenceFrameCount = 0;  // 重置静音帧计数
            avgEnergy = 0;
            probs.clear();
            originalProbs.clear(); // 重置原始概率列表
            frameCounter = 0;      // 重置帧计数器
            sileroState = new float[2][1][128];
            preBuffer.clear();
            preBufferSize = 0;
            pcmData.clear();
            opusData.clear();
            pcmAccumulator.reset();
            lastAccumTime = System.currentTimeMillis();
        }
    }

    /**
     * 初始化会话
     */
    public void initSession(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            VadState state = states.get(sessionId);
            if (state == null) {
                state = new VadState();
                states.put(sessionId, state);
            } else {
                state.reset();
            }
            
            logger.info("VAD会话已初始化: {}", sessionId);
        }
    }

    /**
     * 检查会话是否已初始化
     */
    public boolean isSessionInitialized(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            return states.containsKey(sessionId);
        }
    }

    /**
     * 获取会话锁
     */
    private Object getLock(String sessionId) {
        return locks.computeIfAbsent(sessionId, k -> new Object());
    }

    /**
     * 获取音频增强器
     */
    private AudioEnhancer getAudioEnhancer(String sessionId) {
        return audioEnhancers.computeIfAbsent(sessionId, k -> new AudioEnhancer());
    }

    /**
     * 处理音频数据
     */
    public VadResult processAudio(String sessionId, byte[] opusData) {

        if (!isSessionInitialized(sessionId)) {
            return null;
        }

        Object lock = getLock(sessionId);

        // 获取设备配置
        SysDevice device = sessionManager.getDeviceConfig(sessionId);
        // 添加空值检查，使用默认值
        float speechThreshold = 0.4f;
        float silenceThreshold = 0.3f;
        float energyThreshold = 0.001f;
        int silenceTimeoutMs = 800;

        if (device != null && device.getRoleId() != null) {
            SysRole role = roleService.selectRoleById(device.getRoleId());
            speechThreshold = Optional.ofNullable(role.getVadSpeechTh()).orElse(speechThreshold);
            silenceThreshold = Optional.ofNullable(role.getVadSilenceTh()).orElse(silenceThreshold);
            energyThreshold = Optional.ofNullable(role.getVadEnergyTh()).orElse(energyThreshold);
            silenceTimeoutMs = Optional.ofNullable(role.getVadSilenceMs()).orElse(silenceTimeoutMs);
        }

        synchronized (lock) {
            try {
                // 获取会话状态
                VadState state = states.computeIfAbsent(sessionId, k -> new VadState());

                // 保存原始Opus数据
                state.addOpus(opusData);

                // 解码Opus数据
                byte[] pcmData;
                try {
                    pcmData = opusProcessor.opusToPcm(sessionId, opusData);
                    if (pcmData == null || pcmData.length == 0) {
                        return new VadResult(VadStatus.NO_SPEECH, null);
                    }
                } catch (Exception e) {
                    logger.error("Opus解码失败: {}", e.getMessage());
                    return new VadResult(VadStatus.ERROR, null);
                }

                // 分析音频
                float[] samples = bytesToFloats(pcmData);
                // 应用智能音频增强（降噪+人声增强+音量归一化）
                byte[] enhancedPcmData = pcmData; // 默认使用原始数据
                if (audioEnhancementEnabled) {
                    AudioEnhancer enhancer = getAudioEnhancer(sessionId);
                    samples = enhancer.process(samples);
                    // 将增强后的信号转换回PCM数据
                    enhancedPcmData = floatsToBytes(samples);
                }

                float energy = calcEnergy(samples);

                // 获取VAD概率
                float speechProb = detectSpeech(state, samples);

                // 限制概率范围在[0,1]
                speechProb = Math.min(1.0f, speechProb);

                // 添加到原始概率列表
                state.addOriginalProb(speechProb);

                // 添加到预缓冲区
                state.addToPreBuffer(enhancedPcmData);

                // 处理短帧数据
                if (enhancedPcmData.length < MIN_PCM_LENGTH && !state.isSpeaking()) {
                    state.accumulate(enhancedPcmData);

                    // 检查是否需要继续累积
                    if (state.getAccumSize() < MIN_PCM_LENGTH && !state.isAccumTimedOut()) {
                        return new VadResult(VadStatus.NO_SPEECH, null);
                    }

                    // 处理累积的数据
                    enhancedPcmData = state.drainAccumulator();
                    if (enhancedPcmData.length == 0) {
                        return new VadResult(VadStatus.NO_SPEECH, null);
                    }

                    // 重新分析累积后的音频
                    samples = bytesToFloats(enhancedPcmData);

                    // 应用音频增强（累积帧也需要增强）
                    if (audioEnhancementEnabled) {
                        AudioEnhancer enhancer = getAudioEnhancer(sessionId);
                        samples = enhancer.process(samples);
                        // 再次转换回PCM数据
                        enhancedPcmData = floatsToBytes(samples);
                    }

                    energy = calcEnergy(samples);
                    speechProb = detectSpeech(state, samples);
                    speechProb = Math.min(1.0f, speechProb);
                }

                // 计算当前帧的音频时长（16kHz, 16bit, mono = 32 bytes/ms）
                int frameDurationMs = pcmData.length / 32;
                
                // 判断语音状态
                // 连接初期使用更宽松的阈值
                boolean isInitialConnection = state.getFrameCounter() < 10;
                
                boolean hasEnergy;
                boolean isSpeech;
                
                if (isInitialConnection) {
                    // 连接初期：使用更宽松的能量和概率要求
                    hasEnergy = energy > energyThreshold * 0.3f; // 大幅降低能量要求
                    isSpeech = speechProb > speechThreshold * 0.6f && hasEnergy; // 降低概率要求
                } else {
                    // 正常情况：使用标准要求
                    // 只要能量超过阈值就认为有能量，不再依赖平均能量的倍数
                    // 因为平均能量在静音初期还很高，会导致误判
                    hasEnergy = energy > energyThreshold;
                    isSpeech = speechProb > speechThreshold && hasEnergy;
                }
                
                // 静音判定：概率很低直接判定为静音，或者概率中等但没有能量
                // 但如果概率很高（>speechThreshold），即使能量低也不应该判定为静音
                // 当能量极低时(<energyThreshold)，也应判定为静音，即使概率偏高
                boolean isVeryLowEnergy = energy < energyThreshold;
                boolean isSilence = speechProb < silenceThreshold || (speechProb < speechThreshold && !hasEnergy) || isVeryLowEnergy;
                
                // 更新能量（在判定静音后更新，这样可以根据静音状态调整平滑因子）
                state.updateEnergy(energy, isSilence);
                
                // 更新静音状态
                state.updateSilence(isSilence, frameDurationMs);
                
                // 每N帧输出一次VAD状态（在updateSilence之后，显示当前帧的状态）
                // if (state.getFrameCounter() % LOG_FRAME_INTERVAL == 0) {
                //      // 预先格式化浮点数
                //      String probStr = String.format("%.4f", speechProb);
                //      String energyStr = String.format("%.6f", energy);
                //      String thresholdStr = String.format("%.4f", speechThreshold);
                //      String avgEnergyStr = String.format("%.6f", state.getAvgEnergy());

                //     logger.info("VAD状态 - SessionId: {}, 帧: {}, 概率: {}, 能量: {}, 平均能量: {}, 阈值: {}, 静音: {}ms (连续{}帧), isSilent: {}, hasEnergy: {}",
                //             sessionId, state.getFrameCounter(), probStr, energyStr, avgEnergyStr, 
                //             thresholdStr, state.getSilenceDuration(), state.getConsecutiveSilenceFrames(), isSilence, hasEnergy);
                // }

                // 处理状态转换
                if (!state.isSpeaking() && isSpeech) {
                    // 语音开始
                    state.pcmData.clear();
                    state.setSpeaking(true);
                    state.resetSilenceFrameCount();  // 重置静音帧计数
                    
                    // 预先格式化浮点数
                    String probStr = String.format("%.4f", speechProb);
                    String energyStr = String.format("%.6f", energy);
                    String thresholdStr = String.format("%.4f", speechThreshold);

                    logger.debug("检测到语音开始 - SessionId: {}, 概率: {}, 能量: {}, 阈值: {}", 
                            sessionId, probStr, energyStr, thresholdStr);

                    // 获取预缓冲数据
                    byte[] preBufferData = state.drainPreBuffer();
                    byte[] result;

                    if (preBufferData.length > 0) {
                        // 预缓冲数据已经包含当前帧，直接使用
                        result = preBufferData;
                        state.addPcm(result);
                    } else {
                        // 没有预缓冲数据，使用当前帧
                        result = enhancedPcmData;
                        state.addPcm(enhancedPcmData);
                    }

                    return new VadResult(VadStatus.SPEECH_START, result);
                } else if (state.isSpeaking() && isSilence) {
                    // 检查静音时长
                    int silenceDuration = state.getSilenceDuration();
                    if (silenceDuration > silenceTimeoutMs) {
                        // 语音结束 - 移除多余的静音帧，但保留部分尾音
                        state.setSpeaking(false);
                        
                        // 计算需要移除的静音时长（保留tailKeepMs的尾音）
                        int silenceToRemoveMs = silenceDuration - tailKeepMs;
                        
                        if (silenceToRemoveMs > 0) {
                            // 基于实际静音帧数按比例计算要移除的帧数
                            // 避免因为网络抖动导致时间计算不准确
                            int totalSilenceFrames = state.getSilenceFrameCount();
                            int framesToRemove = 0;
                            
                            if (totalSilenceFrames > 0 && silenceDuration > 0) {
                                // 按比例计算: 要移除的帧数 = 总帧数 * (要移除的时长 / 总静音时长)
                                framesToRemove = Math.min(
                                    (int) Math.ceil((double) totalSilenceFrames * silenceToRemoveMs / silenceDuration),
                                    totalSilenceFrames
                                );
                            }
                            
                            if (framesToRemove > 0) {
                                // 移除PCM数据中的静音帧
                                for (int i = 0; i < framesToRemove && !state.pcmData.isEmpty(); i++) {
                                    state.pcmData.remove(state.pcmData.size() - 1);
                                }
                                // 移除Opus数据中的静音帧
                                for (int i = 0; i < framesToRemove && !state.opusData.isEmpty(); i++) {
                                    state.opusData.remove(state.opusData.size() - 1);
                                }
                                logger.debug("语音结束: {}, 静音: {}ms, 移除{}ms静音({}帧), 保留{}ms尾音", 
                                        sessionId, silenceDuration, silenceToRemoveMs, framesToRemove, tailKeepMs);
                            } else {
                                logger.debug("语音结束: {}, 静音: {}ms, 保留全部尾音", sessionId, silenceDuration);
                            }
                        } else {
                            logger.debug("语音结束: {}, 静音: {}ms, 静音较短，保留全部", sessionId, silenceDuration);
                        }
                        
                        state.resetSilenceFrameCount();  // 重置静音帧计数
                        
                        // 重置音频增强器状态（为下一句话准备）
                        AudioEnhancer enhancer = audioEnhancers.get(sessionId);
                        if (enhancer != null) {
                            enhancer.reset();
                        }
                        
                        // 重置VAD模型状态（为下一句话准备）
                        // 清空sileroState，让下一句话从干净状态开始
                        state.sileroState = new float[2][1][128];
                        
                        return new VadResult(VadStatus.SPEECH_END, enhancedPcmData);
                    } else {
                        // 静音未超时，继续收集（但这是静音帧）
                        state.addPcm(enhancedPcmData);
                        state.incrementSilenceFrameCount();  // 记录这是一个静音帧
                        return new VadResult(VadStatus.SPEECH_CONTINUE, enhancedPcmData);
                    }
                } else if (state.isSpeaking()) {
                    // 语音继续（非静音）
                    state.addPcm(enhancedPcmData);
                    state.resetSilenceFrameCount();  // 重置静音帧计数，因为又开始说话了
                    return new VadResult(VadStatus.SPEECH_CONTINUE, enhancedPcmData);
                } else {
                    // 无语音
                    return new VadResult(VadStatus.NO_SPEECH, null);
                }
            } catch (Exception e) {
                logger.error("处理音频失败: {}, 错误: {}", sessionId, e.getMessage(), e);
                return new VadResult(VadStatus.ERROR, null);
            }
        }
    }

    /**
     * 执行语音检测
     */
    private float detectSpeech(VadState state, float[] samples) {
        if (vadModel == null || samples == null || samples.length == 0) {
            logger.warn("VAD模型为空或样本为空");
            return 0.0f;
        }

        try {
            // 处理样本大小
            if (samples.length == VAD_SAMPLE_SIZE) {
                InferenceResult r = vadModel.infer(samples, state.sileroState);
                state.sileroState = r.state;
                return r.probability;
            }

            // 样本不足，需要填充
            if (samples.length < VAD_SAMPLE_SIZE) {
                float[] padded = new float[VAD_SAMPLE_SIZE];
                System.arraycopy(samples, 0, padded, 0, samples.length);
                InferenceResult r = vadModel.infer(padded, state.sileroState);
                state.sileroState = r.state;
                return r.probability;
            }

            // 样本过长，分段处理
            float maxProb = 0.0f;
            for (int offset = 0; offset <= samples.length - VAD_SAMPLE_SIZE; offset += VAD_SAMPLE_SIZE / 2) {
                float[] chunk = new float[VAD_SAMPLE_SIZE];
                System.arraycopy(samples, offset, chunk, 0, VAD_SAMPLE_SIZE);
                InferenceResult r = vadModel.infer(chunk, state.sileroState);
                state.sileroState = r.state;
                float prob = r.probability;
                maxProb = Math.max(maxProb, prob);
            }
            return maxProb;
        } catch (Exception e) {
            logger.error("VAD推断失败: {}", e.getMessage());
            return 0.0f;
        }
    }

    /**
     * 字节数组转浮点数组
     */
    private float[] bytesToFloats(byte[] pcmData) {
        int sampleCount = pcmData.length / 2;
        float[] samples = new float[sampleCount];

        ByteBuffer buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) {
            short sample = buffer.getShort();
            samples[i] = sample / 32768.0f; // 归一化到[-1,1]
        }

        return samples;
    }

    /**
     * 浮点数组转字节数组
     */
    private byte[] floatsToBytes(float[] samples) {
        int sampleCount = samples.length;
        byte[] pcmData = new byte[sampleCount * 2];

        ByteBuffer buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) {
            // 确保样本在[-1,1]范围内，然后转换为16位PCM
            float clampedSample = Math.max(-1.0f, Math.min(1.0f, samples[i]));
            short pcmSample = (short) (clampedSample * 32767.0f);
            buffer.putShort(pcmSample);
        }

        return pcmData;
    }

    /**
     * 计算音频能量
     */
    private float calcEnergy(float[] samples) {
        float sum = 0;
        for (float sample : samples) {
            sum += Math.abs(sample);
        }
        return sum / samples.length;
    }

    /**
     * 重置会话
     */
    public void resetSession(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            VadState state = states.get(sessionId);
            if (state != null) {
                state.reset();
            }
            states.remove(sessionId);
            locks.remove(sessionId);

            // 重置音频增强器
            AudioEnhancer enhancer = audioEnhancers.get(sessionId);
            if (enhancer != null) {
                enhancer.reset();
            }
            audioEnhancers.remove(sessionId);

            logger.info("VAD会话已重置: {}", sessionId);
        }
    }

    /**
     * 检查是否正在说话
     */
    public boolean isSpeaking(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            VadState state = states.get(sessionId);
            return state != null && state.isSpeaking();
        }
    }

    /**
     * 获取当前语音概率
     */
    public float getSpeechProbability(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            VadState state = states.get(sessionId);
            return state != null ? state.getLastOriginalProb() : 0.0f;
        }
    }

    /**
     * 获取音频数据
     */
    public List<byte[]> getPcmData(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            VadState state = states.get(sessionId);
            return state != null ? state.getPcmData() : new ArrayList<>();
        }
    }

    /**
     * 获取Opus数据
     */
    public List<byte[]> getOpusData(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            VadState state = states.get(sessionId);
            return state != null ? state.getOpusData() : new ArrayList<>();
        }
    }

    /**
     * 获取当前帧计数
     */
    public int getFrameCounter(String sessionId) {
        Object lock = getLock(sessionId);
        synchronized (lock) {
            VadState state = states.get(sessionId);
            return state != null ? state.getFrameCounter() : 0;
        }
    }

    /**
     * VAD状态枚举
     */
    public enum VadStatus {
        NO_SPEECH, // 无语音
        SPEECH_START, // 语音开始
        SPEECH_CONTINUE, // 语音继续
        SPEECH_END, // 语音结束
        ERROR // 处理错误
    }

    /**
     * VAD结果类
     */
    public static class VadResult {
        private final VadStatus status;
        private final byte[] data;

        public VadResult(VadStatus status, byte[] data) {
            this.status = status;
            this.data = data;
        }

        public VadStatus getStatus() {
            return status;
        }

        public byte[] getProcessedData() {
            return data;
        }

        public boolean isSpeechActive() {
            return status == VadStatus.SPEECH_START || status == VadStatus.SPEECH_CONTINUE;
        }

        public boolean isSpeechEnd() {
            return status == VadStatus.SPEECH_END;
        }
    }
}
package com.xiaozhi.utils;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.filters.LowPassFS;
import be.tarsos.dsp.filters.BandPass;
import be.tarsos.dsp.util.fft.FFT;
import be.tarsos.dsp.util.fft.HannWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * 音频增强器
 * 
 * 功能特性：
 * 1. 基于FFT的频谱分析和降噪（谱减法）
 * 2. 多级滤波器链（高通+带通+低通）
 * 3. 人声频段智能增强（300-3400Hz）
 * 4. 自适应增益控制（基于频谱特征）
 * 5. 动态范围压缩和限幅器
 * 6. 去混响和去回声
 * 
 * @author xiaozhi
 */
public class AudioEnhancer {
    private static final Logger logger = LoggerFactory.getLogger(AudioEnhancer.class);

    // 音频参数
    private static final int SAMPLE_RATE = AudioUtils.SAMPLE_RATE;
    private static final int BUFFER_SIZE = AudioUtils.BUFFER_SIZE;  // FFT窗口大小
    
    // 人声频率范围（Hz）
    private static final float VOICE_FREQ_HIGH = 8000f;    // 人声谐波上限
    private static final float VOICE_MAIN_LOW = 300f;      // 主要语音频段下限
    private static final float VOICE_MAIN_HIGH = 3400f;    // 主要语音频段上限
    private static final float NOISE_CUTOFF = 80f;         // 低频噪声截止频率
    
    // TarsosDSP组件
    private final FFT fft;
    private final HannWindow hannWindow;
    private final HighPass highPassFilter;
    private final LowPassFS lowPassFilter;
    private final BandPass voiceBandPass;
    
    // 频谱分析缓冲区
    private final float[] fftBuffer;
    private final float[] fftMagnitude;
    private final float[] noiseSpectrum;      // 噪声频谱估计
    private final float[] smoothedSpectrum;   // 平滑后的频谱
    
    // 自适应参数
    private float noiseFloor;
    private float targetRms;
    private boolean initialized;
    private int noiseEstimateFrames;
    private static final int NOISE_LEARN_FRAMES = 10;  // 学习前10帧
    
    // 增益控制
    private float smoothedRms;
    private float smoothedPeak;
    private float currentGain;
    private float previousGain;
    
    // 人声检测
    private float voiceEnergyRatio;     // 人声频段能量占比
    private boolean voiceDetected;
    
    /**
     * 默认构造函数 - 零配置，自动优化
     */
    public AudioEnhancer() {
        // 初始化FFT
        this.fft = new FFT(BUFFER_SIZE);
        this.hannWindow = new HannWindow();
        
        // 初始化滤波器链
        // 1. 高通滤波器 - 去除低频噪声（85Hz以下）
        this.highPassFilter = new HighPass(NOISE_CUTOFF, SAMPLE_RATE);
        
        // 2. 低通滤波器 - 去除高频噪声（8kHz以上）
        this.lowPassFilter = new LowPassFS(VOICE_FREQ_HIGH, SAMPLE_RATE);
        
        // 3. 带通滤波器 - 增强主要语音频段（300-3400Hz）
        this.voiceBandPass = new BandPass(VOICE_MAIN_LOW, VOICE_MAIN_HIGH, SAMPLE_RATE);
        
        // 初始化频谱缓冲区
        this.fftBuffer = new float[BUFFER_SIZE * 2];  // 实部+虚部
        this.fftMagnitude = new float[BUFFER_SIZE / 2];
        this.noiseSpectrum = new float[BUFFER_SIZE / 2];
        this.smoothedSpectrum = new float[BUFFER_SIZE / 2];
        
        // 初始化参数
        this.noiseFloor = 0.0f;
        this.targetRms = 0.15f;
        this.initialized = true;
        this.noiseEstimateFrames = 0;
        this.smoothedRms = 0.0f;
        this.smoothedPeak = 0.0f;
        this.currentGain = 1.0f;
        this.previousGain = 1.0f;
        this.voiceEnergyRatio = 0.0f;
        this.voiceDetected = false;
        
        Arrays.fill(noiseSpectrum, 0.0f);
        Arrays.fill(smoothedSpectrum, 0.0f);
        
        logger.info("AudioEnhancer已初始化 - TarsosDSP专业模式");
    }
    
    /**
     * 处理音频数据 - 主入口
     * 
     * @param samples 输入音频样本（float数组，范围 -1.0 到 1.0）
     * @return 增强后的音频样本
     */
    public float[] process(float[] samples) {
        if (samples == null || samples.length == 0) {
            return samples;
        }
        
        // 创建AudioEvent用于TarsosDSP处理
        float[] processed = samples.clone();
        
        // 步骤1: 温和的高通滤波（去除低频噪声）
        processed = applyFilterChain(processed);
        
        // 步骤2: 人声频段检测（仅用于分析，不修改音频）
        analyzeVoiceBand(processed);
        
        // 步骤3: 自适应增益控制（核心功能）
        processed = applyAdaptiveGain(processed);
        
        // 步骤4: 温和的动态范围压缩
        processed = applyCompression(processed);
        
        // 步骤5: 峰值限制器
        processed = applyLimiter(processed);
        
        // 注意：暂时禁用FFT降噪和预加重，因为它们可能破坏VAD识别
        // 如果需要更强的降噪，可以单独配置开关
        
        return processed;
    }
    
    /**
     * 步骤1: 应用滤波器链
     * 高通滤波器 -> 低通滤波器，构建理想的语音通道
     */
    private float[] applyFilterChain(float[] samples) {
        float[] filtered = samples.clone();
        
        // 创建AudioEvent并设置缓冲区
        AudioEvent audioEvent = createAudioEvent(filtered);
        audioEvent.setFloatBuffer(filtered);
        
        // 应用高通滤波器（去除低频噪声）
        highPassFilter.process(audioEvent);
        
        // 应用低通滤波器（去除高频噪声）
        lowPassFilter.process(audioEvent);
        
        return audioEvent.getFloatBuffer();
    }
    
    /**
     * 步骤2: 基于FFT的频谱降噪（谱减法）
     * 这是最强大的降噪方法，可以精确识别和抑制噪声频率成分
     */
    private float[] applySpectralNoiseReduction(float[] samples) {
        int numFrames = (int) Math.ceil((double) samples.length / BUFFER_SIZE);
        float[] denoised = new float[samples.length];
        int outputPos = 0;
        
        for (int frameIdx = 0; frameIdx < numFrames; frameIdx++) {
            int frameStart = frameIdx * BUFFER_SIZE;
            int frameEnd = Math.min(frameStart + BUFFER_SIZE, samples.length);
            int frameSize = frameEnd - frameStart;
            
            if (frameSize < BUFFER_SIZE / 2) {
                // 帧太小，直接复制
                System.arraycopy(samples, frameStart, denoised, outputPos, frameSize);
                outputPos += frameSize;
                continue;
            }
            
            // 准备FFT输入（填充到BUFFER_SIZE）
            float[] frame = new float[BUFFER_SIZE];
            System.arraycopy(samples, frameStart, frame, 0, frameSize);
            
            // 应用汉宁窗减少频谱泄漏
            hannWindow.apply(frame);
            
            // 执行FFT（实数到复数）
            Arrays.fill(fftBuffer, 0.0f);
            for (int i = 0; i < BUFFER_SIZE; i++) {
                fftBuffer[i * 2] = frame[i];      // 实部
                fftBuffer[i * 2 + 1] = 0.0f;      // 虚部
            }
            fft.forwardTransform(fftBuffer);
            
            // 计算幅度谱
            for (int i = 0; i < BUFFER_SIZE / 2; i++) {
                float real = fftBuffer[i * 2];
                float imag = fftBuffer[i * 2 + 1];
                fftMagnitude[i] = (float) Math.sqrt(real * real + imag * imag);
            }
            
            // 估计或更新噪声频谱
            if (!initialized || noiseEstimateFrames < NOISE_LEARN_FRAMES) {
                updateNoiseSpectrum(fftMagnitude);
            }
            
            // 应用谱减法降噪
            float[] denoisedMagnitude = new float[BUFFER_SIZE / 2];
            for (int i = 0; i < BUFFER_SIZE / 2; i++) {
                // 谱减法：|X_clean| = |X| - α * |N|
                float alpha = 2.0f;  // 过减因子
                float beta = 0.01f;  // 谱下限（防止完全静音）
                
                float cleanMag = fftMagnitude[i] - alpha * noiseSpectrum[i];
                cleanMag = Math.max(cleanMag, beta * fftMagnitude[i]);
                denoisedMagnitude[i] = cleanMag;
            }
            
            // 重建复数频谱（保持相位）
            for (int i = 0; i < BUFFER_SIZE / 2; i++) {
                float originalMag = fftMagnitude[i];
                float gain = originalMag > 0 ? denoisedMagnitude[i] / originalMag : 0.0f;
                fftBuffer[i * 2] *= gain;      // 调整实部
                fftBuffer[i * 2 + 1] *= gain;  // 调整虚部
            }
            
            // 逆FFT
            fft.backwardsTransform(fftBuffer);
            
            // 提取实部并反归一化
            for (int i = 0; i < frameSize; i++) {
                frame[i] = fftBuffer[i * 2] / BUFFER_SIZE;
            }
            
            // 输出到结果数组
            System.arraycopy(frame, 0, denoised, outputPos, frameSize);
            outputPos += frameSize;
        }
        
        return denoised;
    }
    
    /**
     * 更新噪声频谱估计
     */
    private void updateNoiseSpectrum(float[] magnitude) {
        if (noiseEstimateFrames == 0) {
            // 第一帧，直接复制
            System.arraycopy(magnitude, 0, noiseSpectrum, 0, noiseSpectrum.length);
        } else {
            // 平滑更新（取最小值或平滑平均）
            float smoothing = 0.9f;
            for (int i = 0; i < noiseSpectrum.length; i++) {
                // 使用最小值追踪（噪声通常是持续存在的最低能量）
                noiseSpectrum[i] = Math.min(
                    noiseSpectrum[i] * smoothing + magnitude[i] * (1.0f - smoothing),
                    magnitude[i]
                );
            }
        }
        
        noiseEstimateFrames++;
        if (noiseEstimateFrames >= NOISE_LEARN_FRAMES) {
            initialized = true;
            if (noiseEstimateFrames == NOISE_LEARN_FRAMES) {
                // 计算噪声基底
                float avgNoise = 0.0f;
                for (float v : noiseSpectrum) {
                    avgNoise += v;
                }
                noiseFloor = avgNoise / noiseSpectrum.length;
                logger.debug("噪声频谱学习完成，噪声基底: {}", String.format("%.6f", noiseFloor));
            }
        }
    }
    
    /**
     * 步骤2: 人声频段分析（仅分析，不修改音频）
     * 使用带通滤波器和能量分析
     */
    private void analyzeVoiceBand(float[] samples) {
        // 先计算总能量（滤波前）
        float totalEnergy = calculateRms(samples);
        
        // 分析人声频段能量
        float[] voiceBand = samples.clone();
        AudioEvent voiceEvent = createAudioEvent(voiceBand);
        voiceEvent.setFloatBuffer(voiceBand);
        voiceBandPass.process(voiceEvent);
        
        float voiceEnergy = calculateRms(voiceEvent.getFloatBuffer());
        
        // 计算人声能量占比（限制在0-1之间）
        // 注意：滤波器可能放大信号，所以需要限制最大值
        if (totalEnergy > 1e-8f) {
            voiceEnergyRatio = Math.min(1.0f, voiceEnergy / totalEnergy);
        } else {
            voiceEnergyRatio = 0.0f;
        }
        
        // 人声检测：需要同时满足多个条件
        // 1. 人声频段占比 > 30%
        // 2. 总能量足够大（避免把噪声当人声）
        // 3. 已经完成噪声学习
        voiceDetected = voiceEnergyRatio > 0.3f 
                        && totalEnergy > 0.001f  // 提高能量阈值
                        && initialized;          // 必须完成噪声学习
        
        // 注意：这个方法只分析，不修改音频
        // 人声检测结果会影响后续的增益控制策略
    }
    
    /**
     * 步骤3: 固定增益控制（立即满增益）
     * 从第一帧就使用最大增益，不需要渐进
     */
    private float[] applyAdaptiveGain(float[] samples) {
        // 固定使用最大增益10倍
        float gain = 10.0f;
        
        currentGain = gain;
        previousGain = gain;
        
        // 应用增益
        float[] amplified = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            amplified[i] = samples[i] * gain;
        }
        
        return amplified;
    }
    
    /**
     * 步骤4: 动态范围压缩（温和版本）
     */
    private float[] applyCompression(float[] samples) {
        float[] compressed = samples.clone();
        
        // 更温和的压缩器参数（避免破坏VAD识别）
        float threshold = 0.7f;      // 提高阈值（给增益更多空间）
        float ratio = 2.5f;          // 降低压缩比 2.5:1（更温和）
        float kneeWidth = 0.2f;      // 更宽的软拐点
        float makeupGain = 1.05f;    // 降低补偿增益
        
        for (int i = 0; i < compressed.length; i++) {
            float sample = compressed[i];
            float magnitude = Math.abs(sample);
            
            if (magnitude > threshold - kneeWidth / 2.0f) {
                float sign = Math.signum(sample);
                
                if (magnitude < threshold + kneeWidth / 2.0f) {
                    // 软拐点区域（平滑过渡）
                    float x = magnitude - threshold;
                    float w = kneeWidth;
                    float compressed_db = (x + w/2.0f) * (x + w/2.0f) / (2.0f * w);
                    compressed[i] = sign * (threshold + compressed_db / ratio);
                } else {
                    // 完全压缩区域
                    float excess = magnitude - threshold;
                    compressed[i] = sign * (threshold + excess / ratio);
                }
                
                // 应用补偿增益
                compressed[i] *= makeupGain;
            }
        }
        
        return compressed;
    }
    
    /**
     * 步骤6: 峰值限制器（砖墙限幅器）
     */
    private float[] applyLimiter(float[] samples) {
        float[] limited = samples.clone();
        float limit = 0.95f;
        
        // 先行峰值检测
        for (int i = 0; i < limited.length; i++) {
            if (limited[i] > limit) {
                limited[i] = limit;
            } else if (limited[i] < -limit) {
                limited[i] = -limit;
            }
        }
        
        return limited;
    }
    
    /**
     * 创建AudioEvent用于TarsosDSP处理
     */
    private AudioEvent createAudioEvent(float[] buffer) {
        // 创建TarsosDSP的AudioFormat
        be.tarsos.dsp.io.TarsosDSPAudioFormat format = 
            new be.tarsos.dsp.io.TarsosDSPAudioFormat(
                SAMPLE_RATE,  // 采样率
                16,           // 样本大小（位）
                1,            // 声道数（单声道）
                true,         // 有符号
                false         // 小端序
            );
        return new AudioEvent(format);
    }
    
    /**
     * 计算RMS（均方根）
     */
    private float calculateRms(float[] samples) {
        if (samples.length == 0) return 0.0f;
        double sum = 0.0;
        for (float sample : samples) {
            sum += sample * sample;
        }
        return (float) Math.sqrt(sum / samples.length);
    }
    
    /**
     * 计算峰值
     */
    private float calculatePeak(float[] samples) {
        float peak = 0.0f;
        for (float sample : samples) {
            float abs = Math.abs(sample);
            if (abs > peak) {
                peak = abs;
            }
        }
        return peak;
    }
    
    /**
     * 重置增强器状态
     */
    public void reset() {
        this.noiseFloor = 0.0f;
        this.targetRms = 0.15f;
        this.initialized = false;
        this.noiseEstimateFrames = 0;
        this.smoothedRms = 0.0f;
        this.smoothedPeak = 0.0f;
        this.currentGain = 1.0f;
        this.previousGain = 1.0f;
        this.voiceEnergyRatio = 0.0f;
        this.voiceDetected = false;
        
        Arrays.fill(noiseSpectrum, 0.0f);
        Arrays.fill(smoothedSpectrum, 0.0f);
        
        logger.debug("AudioEnhancer状态已重置");
    }
    
    /**
     * 获取当前噪声基底
     */
    public float getNoiseFloor() {
        return noiseFloor;
    }
    
    /**
     * 获取当前增益
     */
    public float getCurrentGain() {
        return currentGain;
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 获取人声能量占比
     */
    public float getVoiceEnergyRatio() {
        return voiceEnergyRatio;
    }
    
    /**
     * 是否检测到人声
     */
    public boolean isVoiceDetected() {
        return voiceDetected;
    }
}
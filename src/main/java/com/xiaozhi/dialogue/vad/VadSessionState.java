package com.xiaozhi.dialogue.vad;

import java.util.ArrayList;
import java.util.List;

import com.xiaozhi.utils.AudioUtils;

/**
 * VAD会话状态类 - 管理每个会话的VAD状态
 */
public class VadSessionState {
    // 音频缓冲区
    private List<Byte> audioBuffer = new ArrayList<>();
    private List<Byte> preBuffer = new ArrayList<>();

    // 语音检测状态
    private List<Float> probabilities = new ArrayList<>();
    private boolean speaking = false;
    private long lastSpeechTimestamp = 0;
    private int silenceFrameCount = 0;
    private int frameCount = 0;
    private float averageEnergy = 0;
    private int consecutiveSpeechFrames = 0;

    // 配置参数
    private final int requiredConsecutiveFrames = 3;
    private final int maxPreBufferSize = 32000; // 预缓冲区大小 (1秒@16kHz,16位双字节)
    private final int windowSizeSample = AudioUtils.BUFFER_SIZE; // 分析窗口大小
    private final int frameDurationMs = 30; // 每帧持续时间(毫秒)

    /**
     * 添加数据到预缓冲区
     */
    public void addToPrebuffer(byte[] data) {
        for (byte b : data) {
            preBuffer.add(b);
            // 限制预缓冲区大小
            if (preBuffer.size() > maxPreBufferSize) {
                preBuffer.remove(0);
            }
        }
    }

    /**
     * 添加数据到主缓冲区
     */
    public void addToMainBuffer(byte[] data) {
        for (byte b : data) {
            audioBuffer.add(b);
        }
    }

    /**
     * 将预缓冲区的数据转移到主缓冲区
     */
    public void transferPrebufferToMainBuffer() {
        audioBuffer.addAll(preBuffer);
    }

    /**
     * 检查是否有足够的数据进行分析
     */
    public boolean hasEnoughDataForAnalysis() {
        return preBuffer.size() >= windowSizeSample * 2; // 每个样本2字节(16位)
    }

    /**
     * 提取一个窗口的数据用于分析
     */
    public float[] extractSamplesForAnalysis() {
        frameCount++;

        float[] samples = new float[windowSizeSample];

        // 从预缓冲区中提取最新的一个窗口数据
        int startIdx = preBuffer.size() - windowSizeSample * 2;
        for (int i = 0; i < windowSizeSample; i++) {
            // 将两个字节转换为一个short，然后归一化为[-1,1]范围的float
            int idx = startIdx + i * 2;
            short sample = (short) ((preBuffer.get(idx) & 0xFF) |
                    ((preBuffer.get(idx + 1) & 0xFF) << 8));
            samples[i] = sample / 32767.0f;
        }

        return samples;
    }

    /**
     * 更新平均能量
     */
    public void updateAverageEnergy(float currentEnergy) {
        if (averageEnergy == 0) {
            averageEnergy = currentEnergy;
        } else {
            averageEnergy = 0.95f * averageEnergy + 0.05f * currentEnergy;
        }
    }

    /**
     * 添加语音概率
     */
    public void addProbability(float prob) {
        probabilities.add(prob);
    }

    /**
     * 获取最后一个语音概率
     */
    public float getLastProbability() {
        if (probabilities.isEmpty()) {
            return 0.0f;
        }
        return probabilities.get(probabilities.size() - 1);
    }

    /**
     * 增加连续语音帧计数
     */
    public void incrementConsecutiveSpeechFrames() {
        consecutiveSpeechFrames++;
    }

    /**
     * 重置连续语音帧计数
     */
    public void resetConsecutiveSpeechFrames() {
        consecutiveSpeechFrames = 0;
    }

    /**
     * 检查是否应该开始语音
     */
    public boolean shouldStartSpeech() {
        return consecutiveSpeechFrames >= requiredConsecutiveFrames && !speaking;
    }

    /**
     * 增加静音帧计数
     */
    public void incrementSilenceFrames() {
        silenceFrameCount++;
    }

    /**
     * 重置静音帧计数
     */
    public void resetSilenceCount() {
        silenceFrameCount = 0;
        lastSpeechTimestamp = System.currentTimeMillis();
    }

    /**
     * 获取静音持续时间（毫秒）
     */
    public int getSilenceDurationMs() {
        return silenceFrameCount * frameDurationMs;
    }

    /**
     * 获取自上次语音以来的时间（毫秒）
     */
    public long getTimeSinceLastSpeech() {
        return System.currentTimeMillis() - lastSpeechTimestamp;
    }

    /**
     * 获取完整的音频数据
     */
    public byte[] getCompleteAudio() {
        byte[] completeAudio = new byte[audioBuffer.size()];
        for (int i = 0; i < audioBuffer.size(); i++) {
            completeAudio[i] = audioBuffer.get(i);
        }
        return completeAudio;
    }

    /**
     * 检查是否有音频数据
     */
    public boolean hasAudioData() {
        return !audioBuffer.isEmpty();
    }

    /**
     * 重置状态
     */
    public void reset() {
        audioBuffer.clear();
        probabilities.clear();
        speaking = false;
        lastSpeechTimestamp = 0;
        silenceFrameCount = 0;
        frameCount = 0;
        averageEnergy = 0;
        consecutiveSpeechFrames = 0;
        preBuffer.clear();
    }

    // Getter和Setter方法
    public boolean isSpeaking() {
        return speaking;
    }

    public void setSpeaking(boolean speaking) {
        this.speaking = speaking;
        if (speaking) {
            lastSpeechTimestamp = System.currentTimeMillis();
        }
    }

    public float getAverageEnergy() {
        return averageEnergy;
    }

    public List<Float> getProbabilities() {
        return probabilities;
    }
}
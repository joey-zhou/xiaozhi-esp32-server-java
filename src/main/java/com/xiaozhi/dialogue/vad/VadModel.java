package com.xiaozhi.dialogue.vad;

/**
 * VAD模型接口 - 定义VAD模型的基本功能
 */
public interface VadModel {
    /**
     * 初始化VAD模型
     */
    void initialize();

    /**
     * 获取语音概率
     * 
     * @param samples 音频样本数据
     * @return 语音概率 (0.0-1.0)
     */
    float getSpeechProbability(float[] samples);

    /**
     * 进行一次无状态推理：调用方负责管理并传入/接收模型隐状态
     * @param samples 512 个采样点，16kHz 归一化 float
     * @param prevState 上一时刻隐状态，形状 [2][1][128]，允许为 null 表示零状态
     * @return 推理结果，包含概率与新的隐状态
     */
    InferenceResult infer(float[] samples, float[][][] prevState);

    /**
     * 重置模型状态
     */
    void reset();

    /**
     * 关闭模型资源
     */
    void close();

    /**
     * 推理结果
     */
    class InferenceResult {
        public final float probability;
        public final float[][][] state;

        public InferenceResult(float probability, float[][][] state) {
            this.probability = probability;
            this.state = state;
        }
    }
}
package com.xiaozhi.dialogue.audio.vad;

/**
 * VAD模型接口 - 定义VAD模型的基本功能
 */
public interface VadModel {
    /**
     * 初始化VAD模型
     */
    void initialize();

    /**
     * 无状态推理：调用方负责管理并传入/接收模型隐状态
     * @param samples 512 个采样点，16kHz 归一化 float
     * @param prevState 上一时刻隐状态，形状 [2][1][128]，允许为 null 表示零状态
     * @return 推理结果，包含概率与新的隐状态
     */
    InferenceResult infer(float[] samples, float[][][] prevState);

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

package com.xiaozhi.enums;

/**
 * 设备服务端状态机
 * 用于超时保护和整体状态感知，替代原有分散的 playing / musicPlaying / streamingState / inWakeupResponse 布尔字段。
 *
 * 状态流转：
 * IDLE ──(wake/listen)──→ LISTENING ──(speech_end/STT done)──→ THINKING ──(TTS start)──→ SPEAKING ──(TTS stop)──→ IDLE
 *
 * 超时保护规则：只有 IDLE 状态下才允许触发超时断连。
 */
public enum DeviceState {

    /**
     * 待机：设备连接中但无活跃音频交互。
     * 允许触发不活跃超时。
     */
    IDLE,

    /**
     * 聆听：设备正在录音并进行 STT 流式识别。
     * 包含原 streamingState=true 和唤醒词收到后的等待阶段。
     * 不触发超时。
     */
    LISTENING,

    /**
     * 思考：STT 已完成，LLM 正在推理，TTS 尚未开始。
     * 不触发超时。
     */
    THINKING,

    /**
     * 说话：TTS 音频帧正在下发至设备（含唤醒响应阶段）。
     * 不触发超时。
     */
    SPEAKING
}

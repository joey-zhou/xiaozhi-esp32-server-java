package com.xiaozhi.ai.tts;

import lombok.Builder;
import lombok.Getter;

/**
 * TTS 参数配置对象，封装 voiceName/speed/pitch 等参数。
 * 对齐 Spring AI 的 TextToSpeechOptions 设计理念：将散落的 Getter 统一为配置对象。
 * <p>
 * 所有 TTS Provider 在构造时接收此对象，替代原来的 4 个独立参数。
 */
@Getter
@Builder
public class XiaozhiTtsOptions {

    /**
     * 音色名称
     */
    private final String voiceName;

    /**
     * 语速 (0.5-2.0)，1.0 为默认速度
     */
    @Builder.Default
    private final Float speed = 1.0f;

    /**
     * 音调 (0.5-2.0)，1.0 为默认音调
     */
    @Builder.Default
    private final Float pitch = 1.0f;
}

package com.xiaozhi.role;

import com.xiaozhi.common.model.resp.SherpaVoiceResp;

import java.util.List;

/**
 * Sherpa-ONNX 本地音色扫描服务。
 * <p>
 * 扫描配置的本地 TTS 模型目录，自动识别模型类型（Kokoro / Matcha / VITS）和 speaker。
 */
public interface SherpaVoiceService {

    /**
     * 扫描本地 TTS 模型目录，返回所有可用的 sherpa-onnx 音色列表。
     */
    List<SherpaVoiceResp> listVoices();
}

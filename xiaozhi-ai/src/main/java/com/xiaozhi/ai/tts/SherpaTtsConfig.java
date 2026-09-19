package com.xiaozhi.ai.tts;

import com.xiaozhi.ai.tts.providers.SherpaOnnxTtsService;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 本地 sherpa-onnx 合成的进程级参数。
 */
@Configuration
@ConfigurationProperties(prefix = "xiaozhi.tts.sherpa")
@Data
public class SherpaTtsConfig {

    /** 同时进行的本地合成数上限，0 表示按 CPU 核数推导 */
    private int maxConcurrent = 0;

    /** 单次合成的 onnxruntime 线程数；与并发上限的乘积即合成占用的核数 */
    private int numThreads = 1;

    /** 排队等待上限，超时即放弃该句 */
    private long waitTimeoutMs = 8000;

    @PostConstruct
    public void applyToSynthesisExecutor() {
        SherpaOnnxTtsService.configure(maxConcurrent, numThreads, waitTimeoutMs);
    }
}

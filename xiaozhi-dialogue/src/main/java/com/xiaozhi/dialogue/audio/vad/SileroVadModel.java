package com.xiaozhi.dialogue.audio.vad;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtLoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.xiaozhi.utils.AudioUtils;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
/**
 * Silero VAD模型实现
 */
@Slf4j
@Component
public class SileroVadModel implements VadModel {
    public static final int CONTEXT_SIZE = 64;

    @Value("${xiaozhi.vad.model.path:models/silero_vad.onnx}")
    private String modelPath;

    private OrtEnvironment env;
    private OrtSession session;
    private final int windowSize = AudioUtils.BUFFER_SIZE;
    private final int effectiveWindowSize = windowSize + CONTEXT_SIZE;

    @PostConstruct
    @Override
    public void initialize() {
        try {
            env = OrtEnvironment.getEnvironment();
            try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
                opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
                opts.setInterOpNumThreads(1);
                opts.setIntraOpNumThreads(1);
                opts.addCPU(true);

                session = env.createSession(modelPath, opts);
            }

            log.info("Silero VAD模型初始化成功, windowSize={}, contextSize={}, effectiveWindowSize={}", windowSize, CONTEXT_SIZE, effectiveWindowSize);
        } catch (UnsatisfiedLinkError e) {
            log.error("ONNX Runtime native libraries加载失败，请安装Visual C++ Redistributable: {}", e.getMessage());
            log.error("下载地址: https://aka.ms/vs/17/release/vc_redist.x64.exe");
            throw new RuntimeException("ONNX Runtime native libraries加载失败，请安装Visual C++ Redistributable", e);
        } catch (OrtException e) {
            log.error("Silero VAD模型初始化失败", e);
            throw new RuntimeException("VAD模型初始化失败", e);
        }
    }

    @Override
    public InferenceResult infer(float[] samples, float[][][] prevState) {
        return infer(samples, new float[CONTEXT_SIZE], prevState);
    }

    public InferenceResult infer(float[] samples, float[] context, float[][][] prevState) {
        try {
            if (samples.length != windowSize) {
                throw new IllegalArgumentException("样本数量必须是" + windowSize);
            }

            float[][] x = new float[][] { buildInput(samples, context) };

            float[][][] localState = prevState != null ? prevState : new float[2][1][128];

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, x);
            OnnxTensor stateTensor = OnnxTensor.createTensor(env, localState);
            OnnxTensor srTensor = OnnxTensor.createTensor(env, new long[] { AudioUtils.SAMPLE_RATE });

            try {
                try (OrtSession.Result result = session.run(Map.of(
                        "input", inputTensor,
                        "sr", srTensor,
                        "state", stateTensor
                ))) {
                    float[][] output = (float[][]) result.get(0).getValue();
                    float[][][] nextState = (float[][][]) result.get(1).getValue();

                    return new InferenceResult(output[0][0], nextState);
                }
            } finally {
                inputTensor.close();
                stateTensor.close();
                srTensor.close();
            }
        } catch (OrtException e) {
            log.error("VAD模型推理失败", e);
            return new InferenceResult(0.0f, prevState);
        }
    }

    private float[] buildInput(float[] samples, float[] context) {
        float[] input = new float[effectiveWindowSize];
        if (context != null && context.length > 0) {
            int copyLength = Math.min(CONTEXT_SIZE, context.length);
            System.arraycopy(context, context.length - copyLength, input, CONTEXT_SIZE - copyLength, copyLength);
        }
        System.arraycopy(samples, 0, input, CONTEXT_SIZE, samples.length);
        return input;
    }

    @PreDestroy
    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            log.info("Silero VAD模型资源已释放");
        } catch (OrtException e) {
            log.error("关闭VAD模型失败", e);
        }
    }
}

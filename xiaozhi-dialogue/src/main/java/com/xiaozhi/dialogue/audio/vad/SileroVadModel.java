package com.xiaozhi.dialogue.audio.vad;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtLoggingLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.xiaozhi.utils.AudioUtils;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;

/**
 * Silero VAD模型实现
 */
@Component
public class SileroVadModel implements VadModel {
    private static final Logger logger = LoggerFactory.getLogger(SileroVadModel.class);

    @Value("${xiaozhi.vad.model.path:models/silero_vad.onnx}")
    private String modelPath;

    private OrtEnvironment env;
    private OrtSession session;
    private final int windowSize = AudioUtils.BUFFER_SIZE;

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

            logger.info("Silero VAD模型初始化成功");
        } catch (UnsatisfiedLinkError e) {
            logger.error("ONNX Runtime native libraries加载失败，请安装Visual C++ Redistributable: {}", e.getMessage());
            logger.error("下载地址: https://aka.ms/vs/17/release/vc_redist.x64.exe");
            throw new RuntimeException("ONNX Runtime native libraries加载失败，请安装Visual C++ Redistributable", e);
        } catch (OrtException e) {
            logger.error("Silero VAD模型初始化失败", e);
            throw new RuntimeException("VAD模型初始化失败", e);
        }
    }

    @Override
    public InferenceResult infer(float[] samples, float[][][] prevState) {
        try {
            if (samples.length != windowSize) {
                throw new IllegalArgumentException("样本数量必须是" + windowSize);
            }

            float[][] x = new float[][] { samples };

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
            logger.error("VAD模型推理失败", e);
            return new InferenceResult(0.0f, prevState);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            logger.info("Silero VAD模型资源已释放");
        } catch (OrtException e) {
            logger.error("关闭VAD模型失败", e);
        }
    }
}

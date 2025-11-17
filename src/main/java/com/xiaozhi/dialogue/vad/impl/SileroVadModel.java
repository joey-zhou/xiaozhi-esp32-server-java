package com.xiaozhi.dialogue.vad.impl;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtLoggingLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.xiaozhi.dialogue.vad.VadModel;
import com.xiaozhi.utils.AudioUtils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;

/**
 * Silero VAD模型实现
 */
@Component
public class SileroVadModel implements VadModel {
    private static final Logger logger = LoggerFactory.getLogger(SileroVadModel.class);

    @Value("${vad.model.path:models/silero_vad.onnx}")
    private String modelPath;

    private OrtEnvironment env;
    private OrtSession session;
    private float[][][] state;
    private float[][] context;
    private final int windowSize = AudioUtils.BUFFER_SIZE; // 16kHz的窗口大小

    @PostConstruct
    @Override
    public void initialize() {
        try {
            // 初始化ONNX运行时环境
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
            opts.setInterOpNumThreads(1);
            opts.setIntraOpNumThreads(1);
            opts.addCPU(true);

            // 创建会话
            session = env.createSession(modelPath, opts);

            // 初始化状态
            reset();

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
    public float getSpeechProbability(float[] samples) {
        try {
            if (samples.length != windowSize) {
                throw new IllegalArgumentException("样本数量必须是" + windowSize);
            }

            // 准备输入数据
            float[][] x = new float[][] { samples };

            // 创建输入张量
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, x);
            OnnxTensor stateTensor = OnnxTensor.createTensor(env, state);
            OnnxTensor srTensor = OnnxTensor.createTensor(env, new long[] { AudioUtils.SAMPLE_RATE });

            // 准备输入映射
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input", inputTensor);
            inputs.put("sr", srTensor);
            inputs.put("state", stateTensor);

            try {
                // 运行模型
                OrtSession.Result result = session.run(inputs);

                // 获取输出
                float[][] output = (float[][]) result.get(0).getValue();
                state = (float[][][]) result.get(1).getValue();

                // 更新上下文
                context = x;

                // 返回语音概率
                return output[0][0];
            } finally {
                // 释放资源
                inputTensor.close();
                stateTensor.close();
                srTensor.close();
            }
        } catch (OrtException e) {
            logger.error("VAD模型推理失败", e);
            return 0.0f;
        }
    }

    @Override
    public InferenceResult infer(float[] samples, float[][][] prevState) {
        try {
            if (samples.length != windowSize) {
                throw new IllegalArgumentException("样本数量必须是" + windowSize);
            }

            float[][] x = new float[][] { samples };

            float[][][] localState = prevState;
            if (localState == null) {
                localState = new float[2][1][128];
            }

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, x);
            OnnxTensor stateTensor = OnnxTensor.createTensor(env, localState);
            OnnxTensor srTensor = OnnxTensor.createTensor(env, new long[] { AudioUtils.SAMPLE_RATE });

            try {
                OrtSession.Result result = session.run(Map.of(
                        "input", inputTensor,
                        "sr", srTensor,
                        "state", stateTensor
                ));

                float[][] output = (float[][]) result.get(0).getValue();
                float[][][] nextState = (float[][][]) result.get(1).getValue();

                return new InferenceResult(output[0][0], nextState);
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

    @Override
    public void reset() {
        state = new float[2][1][128];
        context = new float[0][];
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
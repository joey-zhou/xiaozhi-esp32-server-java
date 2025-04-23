package com.xiaozhi.utils;

import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PreDestroy;

@Component
public class OpusProcessor {
    private static final Logger logger = LoggerFactory.getLogger(OpusProcessor.class);

    // 存储每个会话的解码器
    private final ConcurrentHashMap<String, OpusDecoder> sessionDecoders = new ConcurrentHashMap<>();

    // 默认的帧大小
    private static final int DEFAULT_FRAME_SIZE = 960; // Opus典型帧大小

    // 默认采样率和通道数
    private static final int DEFAULT_SAMPLE_RATE = 16000;
    private static final int DEFAULT_CHANNELS = 1;

    /**
     * 解码Opus帧为PCM数据
     * 
     * @param sessionId 会话ID，用于复用解码器
     * @param opusData  Opus编码数据
     * @return 解码后的PCM字节数组
     */
    public byte[] decodeOpusFrameToPcm(String sessionId, byte[] opusData) throws OpusException {
        OpusDecoder decoder = getSessionDecoder(sessionId);
        short[] pcmBuffer = new short[DEFAULT_FRAME_SIZE];
        int samplesDecoded = decoder.decode(opusData, 0, opusData.length, pcmBuffer, 0, DEFAULT_FRAME_SIZE, false);

        // 只为实际解码的样本分配内存
        byte[] pcmBytes = new byte[samplesDecoded * 2];
        for (int i = 0; i < samplesDecoded; i++) {
            pcmBytes[i * 2] = (byte) (pcmBuffer[i] & 0xFF);
            pcmBytes[i * 2 + 1] = (byte) ((pcmBuffer[i] >> 8) & 0xFF);
        }

        return pcmBytes;
    }

    /**
     * 解码Opus帧为PCM数据（返回short数组）
     * 
     * @param sessionId 会话ID，用于复用解码器
     * @param opusData  Opus编码数据
     * @return 解码后的PCM short数组
     */
    public short[] decodeOpusFrame(String sessionId, byte[] opusData) throws OpusException {
        OpusDecoder decoder = getSessionDecoder(sessionId);
        short[] pcmBuffer = new short[DEFAULT_FRAME_SIZE];
        int samplesDecoded = decoder.decode(opusData, 0, opusData.length, pcmBuffer, 0, DEFAULT_FRAME_SIZE, false);

        // 如果解码的样本数小于缓冲区大小，创建一个适当大小的数组
        if (samplesDecoded < DEFAULT_FRAME_SIZE) {
            short[] rightSizedBuffer = new short[samplesDecoded];
            System.arraycopy(pcmBuffer, 0, rightSizedBuffer, 0, samplesDecoded);
            return rightSizedBuffer;
        }

        return pcmBuffer;
    }

    /**
     * 获取会话的Opus解码器（如果不存在则创建）
     */
    public OpusDecoder getSessionDecoder(String sessionId) {
        return sessionDecoders.computeIfAbsent(sessionId, k -> {
            try {
                return new OpusDecoder(DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS);
            } catch (OpusException e) {
                logger.error("创建Opus解码器失败", e);
                throw new RuntimeException("创建Opus解码器失败", e);
            }
        });
    }

    /**
     * 创建一个新的Opus编码器
     * 
     * @param sampleRate 采样率
     * @param channels   通道数
     * @return 新创建的Opus编码器
     */
    private OpusEncoder createEncoder(int sampleRate, int channels) {
        try {
            OpusEncoder encoder = new OpusEncoder(sampleRate, channels, OpusApplication.OPUS_APPLICATION_AUDIO);
            encoder.setBitrate(sampleRate); // 设置比特率与采样率相同，更合理
            return encoder;
        } catch (OpusException e) {
            logger.error("创建Opus编码器失败 - 采样率: {}, 通道数: {}", sampleRate, channels, e);
            throw new RuntimeException("创建Opus编码器失败", e);
        }
    }

    /**
     * 将PCM数据转换为Opus格式
     */
    public List<byte[]> convertPcmToOpus(String sessionId, byte[] pcmData, int sampleRate, int channels, int frameDurationMs)
            throws OpusException {
        // 获取或创建Opus编码器
        OpusEncoder encoder = createEncoder(sampleRate, channels);

        // 每帧样本数
        int frameSize = sampleRate * frameDurationMs / 1000;

        // 处理PCM数据
        List<byte[]> opusFrames = new ArrayList<>();
        short[] shortBuffer = new short[frameSize * channels];
        byte[] opusBuffer = new byte[1275]; // 最大Opus帧大小

        for (int i = 0; i < pcmData.length / 2; i += frameSize * channels) {
            // 将字节数据转换为short
            int samplesRead = 0;
            for (int j = 0; j < frameSize * channels && (i + j) < pcmData.length / 2; j++) {
                int byteIndex = (i + j) * 2;
                if (byteIndex + 1 < pcmData.length) {
                    shortBuffer[j] = (short) ((pcmData[byteIndex] & 0xFF) | (pcmData[byteIndex + 1] << 8));
                    samplesRead++;
                }
            }

            // 只有当有足够的样本时才编码
            if (samplesRead > 0) {
                // 编码
                int opusLength = encoder.encode(shortBuffer, 0, frameSize, opusBuffer, 0, opusBuffer.length);

                // 创建正确大小的帧并添加到列表
                byte[] opusFrame = new byte[opusLength];
                System.arraycopy(opusBuffer, 0, opusFrame, 0, opusLength);
                opusFrames.add(opusFrame);
            }
        }

        return opusFrames;
    }

    /**
     * 清理会话资源
     */
    public void cleanupSession(String sessionId) {
        sessionDecoders.remove(sessionId);
    }

    /**
     * 在应用关闭时释放所有资源
     */
    @PreDestroy
    public void cleanup() {
        sessionDecoders.clear();
    }
}
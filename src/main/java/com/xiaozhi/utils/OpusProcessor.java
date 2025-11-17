package com.xiaozhi.utils;

import io.github.jaredmdobson.concentus.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OpusProcessor {
    private static final Logger logger = LoggerFactory.getLogger(OpusProcessor.class);

    // 缓存
    private final ConcurrentHashMap<String, OpusDecoder> decoders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OpusEncoder> encoders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, short[]> overlaps = new ConcurrentHashMap<>();
    // 残留数据状态缓存
    private final ConcurrentHashMap<String, LeftoverState> leftoverStates = new ConcurrentHashMap<>();

    // 常量
    private static final int FRAME_SIZE = AudioUtils.FRAME_SIZE;
    private static final int SAMPLE_RATE = AudioUtils.SAMPLE_RATE;
    private static final int CHANNELS = AudioUtils.CHANNELS;
    public static final int OPUS_FRAME_DURATION_MS = AudioUtils.OPUS_FRAME_DURATION_MS;
    private static final int MAX_SIZE = 1275;


    /**
     * 残留数据状态类
     */
    public static class LeftoverState {
        public short[] leftoverBuffer;
        public int leftoverCount;
        public boolean isFirst = true;

        public LeftoverState() {
            leftoverBuffer = new short[FRAME_SIZE]; // 预分配一个帧大小的缓冲区
            leftoverCount = 0;
        }

        public void clear() {
            leftoverCount = 0;
            Arrays.fill(leftoverBuffer, (short) 0);
        }
    }

    /**
     * 获取会话的残留数据状态
     */
    private LeftoverState getLeftoverState(String sid) {
        return leftoverStates.computeIfAbsent(sid, k -> new LeftoverState());
    }

    /**
     * 删除会话的残留数据状态
     */
    public void removeLeftoverState(String sid) {
        leftoverStates.remove(sid);
    }

    /**
     * 刷新残留数据，生成最后一帧
     */
    public List<byte[]> flushLeftover(String sid) {
        LeftoverState state = getLeftoverState(sid);
        List<byte[]> frames = new ArrayList<>();

        if (state.leftoverCount <= 0) {
            return frames;
        }

        // 获取编码器
        OpusEncoder encoder = getEncoder(sid, SAMPLE_RATE, CHANNELS);

        // 准备缓冲区
        short[] shortBuf = new short[FRAME_SIZE];
        byte[] opusBuf = new byte[MAX_SIZE];

        // 复制残留数据并填充静音
        System.arraycopy(state.leftoverBuffer, 0, shortBuf, 0, state.leftoverCount);
        Arrays.fill(shortBuf, state.leftoverCount, FRAME_SIZE, (short) 0);

        try {
            // 编码最后一帧
            int opusLen = encoder.encode(shortBuf, 0, FRAME_SIZE, opusBuf, 0, opusBuf.length);
            if (opusLen > 0) {
                byte[] frame = new byte[opusLen];
                System.arraycopy(opusBuf, 0, frame, 0, opusLen);
                frames.add(frame);
            }
        } catch (OpusException e) {
            logger.warn("残留数据编码失败: {}", e.getMessage());
        }

        // 清空缓存
        state.clear();
        return frames;
    }

    /**
     * Opus转PCM字节数组
     */
    public byte[] opusToPcm(String sid, byte[] data) throws OpusException {
        if (data == null || data.length == 0) {
            return new byte[0];
        }

        try {
            OpusDecoder decoder = getDecoder(sid);
            short[] buf = new short[FRAME_SIZE * 12];
            int samples = decoder.decode(data, 0, data.length, buf, 0, buf.length, false);

            byte[] pcm = new byte[samples * 2];
            for (int i = 0; i < samples; i++) {
                pcm[i * 2] = (byte) (buf[i] & 0xFF);
                pcm[i * 2 + 1] = (byte) ((buf[i] >> 8) & 0xFF);
            }

            return pcm;
        } catch (OpusException e) {
            logger.warn("解码失败: {}", e.getMessage());
            resetDecoder(sid);
            throw e;
        }
    }

    /**
     * 平滑连接多个PCM片段
     */
    public byte[] smoothJoinPcm(List<byte[]> pcmChunks) {
        if (pcmChunks == null || pcmChunks.isEmpty()) {
            return new byte[0];
        }
        
        // 计算总长度
        int totalLength = 0;
        for (byte[] chunk : pcmChunks) {
            totalLength += chunk.length;
        }
        
        // 创建结果缓冲区
        byte[] result = new byte[totalLength];
        int offset = 0;
        
        // 应用交叉淡入淡出的重叠区域长度（毫秒）
        int overlapMs = 10; // 10ms的重叠
        int overlapBytes = (SAMPLE_RATE * 2 * CHANNELS * overlapMs) / 1000; // 每毫秒的字节数
        
        for (int i = 0; i < pcmChunks.size(); i++) {
            byte[] chunk = pcmChunks.get(i);
            
            if (i == 0) {
                // 第一个片段直接复制
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            } else {
                // 后续片段需要与前一个片段进行平滑过渡
                int overlapStart = Math.max(0, offset - overlapBytes);
                int overlapLength = Math.min(overlapBytes, offset - overlapStart);
                
                if (overlapLength > 0 && chunk.length > 0) {
                    // 在重叠区域应用线性交叉淡变
                    for (int j = 0; j < overlapLength; j += 2) {
                        // 计算淡变权重
                        float weight = (float)j / overlapLength;
                        
                        // 获取重叠区域的样本
                        short sample1 = (short)((result[overlapStart + j] & 0xFF) | ((result[overlapStart + j + 1] & 0xFF) << 8));
                        short sample2 = (short)((chunk[j] & 0xFF) | ((chunk[j + 1] & 0xFF) << 8));
                        
                        // 线性混合
                        short mixed = (short)((1 - weight) * sample1 + weight * sample2);
                        
                        // 写回结果
                        result[overlapStart + j] = (byte)(mixed & 0xFF);
                        result[overlapStart + j + 1] = (byte)((mixed >> 8) & 0xFF);
                    }
                    
                    // 复制剩余部分
                    System.arraycopy(chunk, overlapLength, result, offset, chunk.length - overlapLength);
                    offset += (chunk.length - overlapLength);
                } else {
                    // 没有足够的重叠，直接复制
                    System.arraycopy(chunk, 0, result, offset, chunk.length);
                    offset += chunk.length;
                }
            }
        }
        
        // 如果实际长度小于预分配长度，则裁剪
        if (offset < totalLength) {
            byte[] trimmed = new byte[offset];
            System.arraycopy(result, 0, trimmed, 0, offset);
            return trimmed;
        }
        
        return result;
    }

    /**
     * Opus转short数组
     */
    public short[] opusToShort(String sid, byte[] data) throws OpusException {
        if (data == null || data.length == 0) {
            return new short[0];
        }

        try {
            OpusDecoder decoder = getDecoder(sid);
            short[] buf = new short[FRAME_SIZE * 6];
            int samples = decoder.decode(data, 0, data.length, buf, 0, buf.length, false);

            if (samples < buf.length) {
                short[] result = new short[samples];
                System.arraycopy(buf, 0, result, 0, samples);
                return result;
            }

            return buf;
        } catch (OpusException e) {
            logger.warn("解码失败: {}", e.getMessage());
            resetDecoder(sid);
            throw e;
        }
    }

    /**
     * OGG转PCM
     */
    public byte[] oggToPcm(String sid, byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }

        try {
            // 检查OGG格式
            if (data.length < 4 || data[0] != 'O' || data[1] != 'g' || data[2] != 'g' || data[3] != 'S') {
                try {
                    // 尝试直接解码
                    return opusToPcm(sid, data);
                } catch (OpusException e) {
                    logger.warn("非OGG格式解码失败: {}", e.getMessage());
                    return new byte[0];
                }
            }

            // 解析OGG
            List<byte[]> packets = parseOgg(data);

            if (packets.isEmpty()) {
                logger.warn("OGG中无数据包");
                return new byte[0];
            }

            // 解码所有包
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            for (byte[] packet : packets) {
                try {
                    // 跳过Opus头
                    if (packet.length > 8 && packet[0] == 'O' && packet[1] == 'p'
                            && packet[2] == 'u' && packet[3] == 's') {
                        continue;
                    }

                    byte[] pcm = opusToPcm(sid, packet);
                    if (pcm.length > 0) {
                        out.write(pcm);
                    }
                } catch (OpusException e) {
                    logger.warn("包解码失败: {}", e.getMessage());
                }
            }

            return out.toByteArray();

        } catch (Exception e) {
            logger.error("OGG解码错误", e);
            return new byte[0];
        }
    }

    /**
     * 解析OGG格式
     */
    private List<byte[]> parseOgg(byte[] data) {
        List<byte[]> packets = new ArrayList<>();
        ByteArrayInputStream in = new ByteArrayInputStream(data);

        byte[] pattern = new byte[4];
        byte[] segments = new byte[255];

        while (in.available() > 0) {
            try {
                // 读取OGG标识
                if (in.read(pattern, 0, 4) < 4)
                    break;

                // 检查标识
                if (pattern[0] != 'O' || pattern[1] != 'g' || pattern[2] != 'g' || pattern[3] != 'S') {
                    // 查找下一个标识
                    int b;
                    boolean found = false;
                    while ((b = in.read()) != -1) {
                        if (b == 'O') {
                            if (in.read(pattern, 0, 3) < 3)
                                break;
                            if (pattern[0] == 'g' && pattern[1] == 'g' && pattern[2] == 'S') {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found)
                        break;
                }

                // 跳过版本和标志
                in.skip(2);

                // 跳过粒度位置、序列号、页序号、校验和
                in.skip(16);

                // 读取分段数
                int segCount = in.read();
                if (segCount == -1)
                    break;

                // 读取分段表
                if (in.read(segments, 0, segCount) < segCount)
                    break;

                // 计算数据长度
                int dataLen = 0;
                for (int i = 0; i < segCount; i++) {
                    dataLen += segments[i] & 0xFF;
                }

                // 读取数据
                byte[] pageData = new byte[dataLen];
                int read = in.read(pageData, 0, dataLen);
                if (read < dataLen) {
                    byte[] actual = new byte[read];
                    System.arraycopy(pageData, 0, actual, 0, read);
                    pageData = actual;
                }

                // 解析数据包
                int offset = 0;
                int packetLen = 0;

                for (int i = 0; i < segCount; i++) {
                    int segLen = segments[i] & 0xFF;
                    packetLen += segLen;

                    // 如果段长不是255，表示包结束
                    if (segLen != 255) {
                        if (packetLen > 0 && offset + packetLen <= pageData.length) {
                            byte[] packet = new byte[packetLen];
                            System.arraycopy(pageData, offset, packet, 0, packetLen);
                            packets.add(packet);
                        }
                        offset += packetLen;
                        packetLen = 0;
                    }
                }

                // 处理最后一个包
                if (packetLen > 0 && offset + packetLen <= pageData.length) {
                    byte[] packet = new byte[packetLen];
                    System.arraycopy(pageData, offset, packet, 0, packetLen);
                    packets.add(packet);
                }
            } catch (Exception e) {
                logger.warn("OGG解析错误: {}", e.getMessage());
            }
        }

        return packets;
    }



    /**
     * 获取解码器
     */
    public OpusDecoder getDecoder(String sid) {
        return decoders.computeIfAbsent(sid, k -> {
            try {
                OpusDecoder decoder = new OpusDecoder(SAMPLE_RATE, CHANNELS);
                decoder.setGain(3);
                return decoder;
            } catch (OpusException e) {
                logger.error("创建解码器失败", e);
                throw new RuntimeException("创建解码器失败", e);
            }
        });
    }

    /**
     * 重置解码器
     */
    public void resetDecoder(String sid) {
        decoders.remove(sid);
        try {
            getDecoder(sid);
        } catch (Exception e) {
            logger.error("重置解码器失败", e);
        }
    }

    /**
     * PCM转Opus - 改进版，支持残留数据处理
     */
    public List<byte[]> pcmToOpus(String sid, byte[] pcm, boolean isStream) {
        if (pcm == null || pcm.length == 0) {
            return new ArrayList<>();
        }

        // 确保PCM长度是偶数
        int pcmLen = pcm.length;
        if (pcmLen % 2 != 0) {
            pcmLen--;
        }

        // 每帧样本数
        int frameSize = FRAME_SIZE;

        // 获取编码器
        OpusEncoder encoder = getEncoder(sid, SAMPLE_RATE, CHANNELS);

        // 处理PCM
        List<byte[]> frames = new ArrayList<>();

        // 获取残留数据状态
        LeftoverState state = getLeftoverState(sid);

        // 字节序处理
        ByteBuffer pcmBuf = ByteBuffer.wrap(pcm, 0, pcmLen).order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer inputShorts = pcmBuf.asShortBuffer();
        int totalInputSamples = inputShorts.remaining();

        // 合并残留数据与当前输入
        short[] combined;
        // 缓冲区
        short[] shortBuf = new short[frameSize];
        byte[] opusBuf = new byte[MAX_SIZE];

        if (isStream) {
            if (state.leftoverCount > 0 || !state.isFirst) {
                combined = new short[state.leftoverCount + totalInputSamples];
                System.arraycopy(state.leftoverBuffer, 0, combined, 0, state.leftoverCount);
                inputShorts.get(combined, state.leftoverCount, totalInputSamples);
            } else {
                combined = new short[totalInputSamples];
                inputShorts.get(combined);
                state.isFirst = false;
            }
        } else {
            combined = new short[totalInputSamples];
            inputShorts.get(combined);
        }

        int availableSamples = combined.length;
        int frameCount = availableSamples / frameSize;
        int remainingSamples = availableSamples % frameSize;

        // 处理第一帧 - 如果是新的音频段，应用淡入效果
        if (frameCount > 0 && state.isFirst) {
            System.arraycopy(combined, 0, shortBuf, 0, frameSize);

            // 应用淡入效果 - 前20毫秒（大约320个样本）
            int fadeInSamples = Math.min(320, frameSize);
            for (int i = 0; i < fadeInSamples; i++) {
                // 线性淡入
                float gain = (float) i / fadeInSamples;
                shortBuf[i] = (short) (shortBuf[i] * gain);
            }

            try {
                int opusLen = encoder.encode(shortBuf, 0, frameSize, opusBuf, 0, opusBuf.length);
                if (opusLen > 0) {
                    frames.add(Arrays.copyOf(opusBuf, opusLen));
                }
            } catch (OpusException e) {
                logger.warn("淡入帧编码失败: {}", e.getMessage());
            }

            // 处理剩余的完整帧
            for (int i = 1; i < frameCount; i++) {
                int start = i * frameSize;
                System.arraycopy(combined, start, shortBuf, 0, frameSize);
                try {
                    int opusLen = encoder.encode(shortBuf, 0, frameSize, opusBuf, 0, opusBuf.length);
                    if (opusLen > 0) {
                        frames.add(Arrays.copyOf(opusBuf, opusLen));
                    }
                } catch (OpusException e) {
                    logger.warn("帧 #{} 编码失败: {}", i, e.getMessage());
                }
            }
        } else {
            // 处理所有完整帧
            for (int i = 0; i < frameCount; i++) {
                int start = i * frameSize;
                System.arraycopy(combined, start, shortBuf, 0, frameSize);
                try {
                    int opusLen = encoder.encode(shortBuf, 0, frameSize, opusBuf, 0, opusBuf.length);
                    if (opusLen > 0) {
                        frames.add(Arrays.copyOf(opusBuf, opusLen));
                    }
                } catch (Exception e) {
                    logger.warn("帧 #{} 编码失败: {}", i, e.getMessage());
                }
            }
        }

        if (isStream) {
            // 缓存剩余样本
            state.leftoverCount = remainingSamples;
            if (remainingSamples > 0) {
                if (state.leftoverBuffer.length < remainingSamples) {
                    state.leftoverBuffer = new short[frameSize]; // 确保缓冲区足够大
                }
                System.arraycopy(combined, frameCount * frameSize, state.leftoverBuffer, 0, remainingSamples);
            } else {
                Arrays.fill(state.leftoverBuffer, (short) 0); // 清空
            }
        }
        return frames;
    }


    /**
     * 清理会话
     */
    public void cleanup(String sid) {
        decoders.remove(sid);
        overlaps.remove(sid);
        leftoverStates.remove(sid); // 清理残留数据状态

        // 清理编码器
        List<String> toRemove = new ArrayList<>();
        for (String key : encoders.keySet()) {
            if (key.startsWith(sid + "_")) {
                toRemove.add(key);
            }
        }

        for (String key : toRemove) {
            encoders.remove(key);
        }
    }

    /**
     * 获取编码器
     */
    private OpusEncoder getEncoder(String sid, int rate, int channels) {
        return encoders.computeIfAbsent(sid, k -> {
            try {
                OpusEncoder encoder = new OpusEncoder(rate, channels, OpusApplication.OPUS_APPLICATION_VOIP);

                // 优化设置
                encoder.setBitrate(AudioUtils.BITRATE);
                // 这里后续看是不是要针对音乐做一个切换
                encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
                encoder.setComplexity(5); // 复杂度高音质好，低速度快
                encoder.setPacketLossPercent(0); // 降低丢包补偿，减少处理延迟
                encoder.setForceChannels(channels);
                encoder.setUseVBR(false); // 使用CBR模式确保稳定的比特率
                encoder.setUseDTX(false); // 禁用DTX以确保连续的帧

                return encoder;
            } catch (OpusException e) {
                logger.error("创建编码器失败: 采样率={}, 通道={}", rate, channels, e);
                throw new RuntimeException("创建编码器失败", e);
            }
        });
    }

    /**
     * 释放资源
     */
    @PreDestroy
    public void cleanup() {
        decoders.clear();
        encoders.clear();
        overlaps.clear();
        leftoverStates.clear(); // 清理所有残留数据状态
    }
}
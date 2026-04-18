package com.xiaozhi.utils;

import io.github.jaredmdobson.concentus.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
/**
 * Opus音频处理器
 * 编码、解码，通常是两个过程，只是可能会共享基本设置，例如采样率，频道数，帧大小。
 * 后续如果需要优化，可以考虑拆分成三个工具类。
 * 没必要放在Spring Context管理，没有必要作为 @Component 。作为一个过程工具，用完即扔。
 * 一般工具类（或工具类实例对象），没有必要作为长生命周期的对象。
 */
@Slf4j
public class OpusProcessor {
    // 缓存
    private OpusDecoder decoders = initDecoder();
    private final OpusEncoder encoders = initEncoder();

    // 残留数据状态缓存
    private final LeftoverState leftoverStates = new LeftoverState();

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
     * 刷新残留数据，生成最后一帧
     */
    public List<byte[]> flushLeftover() {
        LeftoverState state = leftoverStates;
        List<byte[]> frames = new ArrayList<>();

        if (state.leftoverCount <= 0) {
            return frames;
        }

        // 获取编码器
        OpusEncoder encoder = encoders;

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
            log.warn("残留数据编码失败: {}", e.getMessage());
        }

        // 清空缓存
        state.clear();
        return frames;
    }

    /**
     * Opus转PCM字节数组
     */
    public byte[] opusToPcm(byte[] data) throws OpusException {
        if (data == null || data.length == 0) {
            return new byte[0];
        }

        try {
            OpusDecoder decoder = decoders;
            short[] buf = new short[FRAME_SIZE * 12];
            int samples = decoder.decode(data, 0, data.length, buf, 0, buf.length, false);

            byte[] pcm = new byte[samples * 2];
            for (int i = 0; i < samples; i++) {
                pcm[i * 2] = (byte) (buf[i] & 0xFF);
                pcm[i * 2 + 1] = (byte) ((buf[i] >> 8) & 0xFF);
            }

            return pcm;
        } catch (OpusException e) {
            log.warn("解码失败: {}", e.getMessage());
            // 重置解码器
            decoders = initDecoder();
            throw e;
        }
    }

    /**
     * PCM转Opus
     */
    public List<byte[]> pcmToOpus(byte[] pcm, boolean isStream) {
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
        OpusEncoder encoder = encoders;

        // 处理PCM
        List<byte[]> frames = new ArrayList<>();

        // 获取残留数据状态
        LeftoverState state = leftoverStates;

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
            } catch (Exception | AssertionError e) {
                log.warn("淡入帧编码失败: {}", e.getMessage());
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
                } catch (Exception | AssertionError e) {
                    log.warn("帧 #{} 编码失败: {}", i, e.getMessage());
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
                } catch (Exception | AssertionError e) {
                    log.warn("帧 #{} 编码失败: {}", i, e.getMessage());
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
     * 获取解码器
     */
    public OpusDecoder initDecoder() {
        try {
            OpusDecoder decoder = new OpusDecoder(SAMPLE_RATE, CHANNELS);
            decoder.setGain(0);
            return decoder;
        } catch (OpusException e) {
            log.error("创建解码器失败", e);
            throw new RuntimeException("创建解码器失败", e);
        }
    }

    /**
     * 获取编码器
     */
    private OpusEncoder initEncoder() {
        try {
            // 使用AUDIO应用以获得更高保真度（TTS更接近有声内容）
            OpusEncoder encoder = new OpusEncoder(SAMPLE_RATE, CHANNELS, OpusApplication.OPUS_APPLICATION_AUDIO);

            // 优化设置
            encoder.setBitrate(AudioUtils.BITRATE);
            // 信号类型保持语音，以便语音相关优化仍生效
            encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
            // 提升复杂度以提高编码质量
            encoder.setComplexity(10);
            // 在网络允许的情况下启用VBR以提升感知质量
            encoder.setUseVBR(true);
            // 如有需要可设置期望VBR上限：encoder.setMaxBandwidth(OpusBandwidth.OPUS_BANDWIDTH_NARROWBAND);
            // 丢包补偿依据场景设置，这里保持0
            encoder.setPacketLossPercent(0);
            encoder.setForceChannels(CHANNELS);
            // 继续禁用DTX以保持连续输出，避免静音期间突兀
            encoder.setUseDTX(false);

            return encoder;
        } catch (OpusException e) {
            log.error("创建编码器失败: 采样率={}, 通道={}", SAMPLE_RATE, CHANNELS, e);
            throw new RuntimeException("创建编码器失败", e);
        }
    }

}
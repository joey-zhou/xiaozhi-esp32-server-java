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
    // 编解码器惰性构造：只解码的实例（VAD 上行、AEC 参考兜底）不建编码器，只编码的实例（播放器）不建解码器。
    // 构造在 initLock 下互斥、经 volatile 字段发布，同一实例先后被不同线程使用也不会重复构造或读到半成品
    private volatile OpusDecoder decoder;
    private volatile OpusEncoder encoder;
    private final Object initLock = new Object();

    // 残留数据状态缓存
    private final LeftoverState leftoverStates = new LeftoverState();

    // 常量
    private static final int FRAME_SIZE = AudioUtils.FRAME_SIZE;
    private static final int SAMPLE_RATE = AudioUtils.SAMPLE_RATE;
    private static final int CHANNELS = AudioUtils.CHANNELS;
    public static final int OPUS_FRAME_DURATION_MS = AudioUtils.OPUS_FRAME_DURATION_MS;
    private static final int MAX_SIZE = 1275;

    /** 一帧静音的 PCM，既用于生成静音帧，也直接充当静音帧的 AEC 参考信号 */
    private static final byte[] SILENCE_PCM = new byte[FRAME_SIZE * 2];

    /**
     * 单个 Opus 包解码后的最大样本数。Opus 一个包最长 120ms，按解码器的采样率算就是上限；
     * 原先按 FRAME_SIZE * 12 取，是这个上限的 6 倍，每帧都要白分配一块 23KB 的临时数组。
     */
    private static final int MAX_DECODED_SAMPLES = SAMPLE_RATE / 1000 * 120 * CHANNELS;

    /**
     * 解码复用的样本缓冲。OpusDecoder 本身就有跨帧状态、不能并发调用，
     * 每个 OpusProcessor 实例都归单条链路独占，所以这块缓冲可以跟着实例一起复用。
     */
    private final short[] decodeBuffer = new short[MAX_DECODED_SAMPLES];

    /**
     * 一帧 Opus 及其编码前的 PCM（16bit 小端）。
     * 服务端 AEC 需要的参考信号就是这份 PCM，带着它一起交给调用方，
     * 下行链路才不用把刚编好的帧再解码一遍。
     */
    public record EncodedFrame(byte[] opus, byte[] pcm) {}

    /**
     * 残留数据状态类
     */
    public static class LeftoverState {
        public short[] leftoverBuffer;
        public int leftoverCount;

        public LeftoverState() {
            leftoverBuffer = new short[FRAME_SIZE]; // 预分配一个帧大小的缓冲区
            leftoverCount = 0;
        }

        public void clear() {
            leftoverCount = 0;
            Arrays.fill(leftoverBuffer, (short) 0);
        }
    }

    private static final class SilenceFrameHolder {
        static final byte[] FRAME = new OpusProcessor().pcmToOpus(SILENCE_PCM, false).get(0).opus();
    }

    /**
     * 一帧（60ms）静音的 Opus 编码，由独立编码器一次性生成，调用方不得修改返回的数组
     */
    public static byte[] silenceFrame() {
        return SilenceFrameHolder.FRAME;
    }

    /**
     * 一帧（60ms）静音的 PCM，即 {@link #silenceFrame()} 的源信号，调用方不得修改返回的数组
     */
    public static byte[] silencePcm() {
        return SILENCE_PCM;
    }

    /**
     * 丢弃流式编码的残留样本，不产生任何帧。
     * 打断时调用，避免上一轮未成帧的尾音被拼进下一轮首帧。
     */
    public void discardLeftover() {
        leftoverStates.clear();
    }

    /**
     * 刷新残留数据，生成最后一帧
     */
    public List<EncodedFrame> flushLeftover() {
        LeftoverState state = leftoverStates;
        List<EncodedFrame> frames = new ArrayList<>();

        if (state.leftoverCount <= 0) {
            return frames;
        }

        // 准备缓冲区
        short[] shortBuf = new short[FRAME_SIZE];
        byte[] opusBuf = new byte[MAX_SIZE];

        // 复制残留数据并填充静音
        System.arraycopy(state.leftoverBuffer, 0, shortBuf, 0, state.leftoverCount);
        Arrays.fill(shortBuf, state.leftoverCount, FRAME_SIZE, (short) 0);

        try {
            // 编码最后一帧
            int opusLen = encoder().encode(shortBuf, 0, FRAME_SIZE, opusBuf, 0, opusBuf.length);
            if (opusLen > 0) {
                frames.add(new EncodedFrame(Arrays.copyOf(opusBuf, opusLen), toPcmBytes(shortBuf, FRAME_SIZE)));
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
            short[] buf = decodeBuffer;
            int samples = decoder().decode(data, 0, data.length, buf, 0, buf.length, false);
            return toPcmBytes(buf, samples);
        } catch (OpusException e) {
            log.warn("解码失败: {}", e.getMessage());
            // 重置解码器
            synchronized (initLock) {
                decoder = initDecoder();
            }
            throw e;
        }
    }

    /**
     * short 样本转 16bit 小端 PCM 字节
     */
    private static byte[] toPcmBytes(short[] samples, int count) {
        byte[] pcm = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            pcm[i * 2] = (byte) (samples[i] & 0xFF);
            pcm[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        return pcm;
    }

    /**
     * PCM转Opus
     */
    public List<EncodedFrame> pcmToOpus(byte[] pcm, boolean isStream) {
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
        OpusEncoder enc = encoder();

        // 处理PCM
        List<EncodedFrame> frames = new ArrayList<>();

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

        if (isStream && state.leftoverCount > 0) {
            // 流式路径把上一次不足一帧的残留拼在本次输入之前
            combined = new short[state.leftoverCount + totalInputSamples];
            System.arraycopy(state.leftoverBuffer, 0, combined, 0, state.leftoverCount);
            inputShorts.get(combined, state.leftoverCount, totalInputSamples);
        } else {
            combined = new short[totalInputSamples];
            inputShorts.get(combined);
        }

        int availableSamples = combined.length;
        int frameCount = availableSamples / frameSize;
        int remainingSamples = availableSamples % frameSize;

        // 逐帧编码
        for (int i = 0; i < frameCount; i++) {
            int start = i * frameSize;
            System.arraycopy(combined, start, shortBuf, 0, frameSize);
            try {
                int opusLen = enc.encode(shortBuf, 0, frameSize, opusBuf, 0, opusBuf.length);
                if (opusLen > 0) {
                    frames.add(new EncodedFrame(Arrays.copyOf(opusBuf, opusLen), toPcmBytes(shortBuf, frameSize)));
                }
            } catch (Exception | AssertionError e) {
                log.warn("帧 #{} 编码失败: {}", i, e.getMessage());
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
     * 取解码器，首次使用时才构造
     */
    private OpusDecoder decoder() {
        OpusDecoder current = decoder;
        if (current != null) {
            return current;
        }
        synchronized (initLock) {
            if (decoder == null) {
                decoder = initDecoder();
            }
            return decoder;
        }
    }

    /**
     * 取编码器，首次使用时才构造
     */
    private OpusEncoder encoder() {
        OpusEncoder current = encoder;
        if (current != null) {
            return current;
        }
        synchronized (initLock) {
            if (encoder == null) {
                encoder = initEncoder();
            }
            return encoder;
        }
    }

    /**
     * 获取解码器
     */
    private OpusDecoder initDecoder() {
        try {
            OpusDecoder dec = new OpusDecoder(SAMPLE_RATE, CHANNELS);
            dec.setGain(0);
            return dec;
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
            OpusEncoder enc = new OpusEncoder(SAMPLE_RATE, CHANNELS, OpusApplication.OPUS_APPLICATION_AUDIO);

            // 优化设置
            enc.setBitrate(AudioUtils.BITRATE);
            // 信号类型保持语音，以便语音相关优化仍生效
            enc.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
            // 复杂度：实时语音取中档，再往上编码耗时明显增加而 48kbps 下听感几乎不变
            enc.setComplexity(AudioUtils.OPUS_COMPLEXITY);
            // 在网络允许的情况下启用VBR以提升感知质量
            enc.setUseVBR(true);
            // 如有需要可设置期望VBR上限：enc.setMaxBandwidth(OpusBandwidth.OPUS_BANDWIDTH_NARROWBAND);
            // 丢包补偿依据场景设置，这里保持0
            enc.setPacketLossPercent(0);
            enc.setForceChannels(CHANNELS);
            // 继续禁用DTX以保持连续输出，避免静音期间突兀
            enc.setUseDTX(false);

            return enc;
        } catch (OpusException e) {
            log.error("创建编码器失败: 采样率={}, 通道={}", SAMPLE_RATE, CHANNELS, e);
            throw new RuntimeException("创建编码器失败", e);
        }
    }

}
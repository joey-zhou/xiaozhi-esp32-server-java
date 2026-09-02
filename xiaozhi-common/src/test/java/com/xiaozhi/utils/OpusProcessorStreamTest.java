package com.xiaozhi.utils;

import io.github.jaredmdobson.concentus.OpusException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住流式编码的帧长不变量：下行音频、服务端 AEC 参考帧、句子边界补帧都按 60ms/960 样本一帧对齐，
 * 残留样本必须跨调用拼接而不是丢弃或补零，否则参考帧与设备播放点会错位。
 * 同时钉住流式与非流式对同一段 PCM 必须编出相同的帧。
 */
class OpusProcessorStreamTest {

    private static final int FRAME_SIZE = AudioUtils.FRAME_SIZE;

    /** 生成指定样本数的16bit小端单声道PCM */
    private static byte[] pcm(int samples) {
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            short v = (short) (Math.sin(2 * Math.PI * 440 * i / AudioUtils.SAMPLE_RATE) * 12000);
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return pcm;
    }

    private static OpusProcessor.LeftoverState stateOf(OpusProcessor processor) {
        return (OpusProcessor.LeftoverState) ReflectionTestUtils.getField(processor, "leftoverStates");
    }

    @Test
    void streamingLeftoverIsCarriedToNextCall() {
        OpusProcessor processor = new OpusProcessor();

        assertThat(processor.pcmToOpus(pcm(FRAME_SIZE + 40), true)).hasSize(1);
        assertThat(stateOf(processor).leftoverCount).isEqualTo(40);

        // 第二次只送 920 个样本，与残留的 40 个拼成整帧
        assertThat(processor.pcmToOpus(pcm(FRAME_SIZE - 40), true)).hasSize(1);
        assertThat(stateOf(processor).leftoverCount).isZero();
        assertThat(stateOf(processor).leftoverBuffer).containsOnly((short) 0);
    }

    @Test
    void streamingChunkShorterThanOneFrameEmitsNothingAndBuffersAll() {
        OpusProcessor processor = new OpusProcessor();

        assertThat(processor.pcmToOpus(pcm(100), true)).isEmpty();
        assertThat(stateOf(processor).leftoverCount).isEqualTo(100);

        assertThat(processor.pcmToOpus(pcm(FRAME_SIZE - 100), true)).hasSize(1);
        assertThat(stateOf(processor).leftoverCount).isZero();
    }

    @Test
    void nonStreamingCallDropsRemainderInsteadOfBuffering() {
        OpusProcessor processor = new OpusProcessor();

        assertThat(processor.pcmToOpus(pcm(FRAME_SIZE + 40), false)).hasSize(1);
        assertThat(stateOf(processor).leftoverCount).isZero();
        assertThat(processor.flushLeftover()).isEmpty();
    }

    @Test
    void flushLeftoverEmitsExactlyOnePaddedFrameAndClearsBuffer() throws OpusException {
        OpusProcessor processor = new OpusProcessor();
        processor.pcmToOpus(pcm(FRAME_SIZE + 40), true);
        assertThat(stateOf(processor).leftoverCount).isEqualTo(40);

        List<byte[]> tail = processor.flushLeftover();

        assertThat(tail).hasSize(1);
        // 残留样本必须补静音凑满一整帧，解码回来仍是 960 个样本
        assertThat(new OpusProcessor().opusToPcm(tail.get(0))).hasSize(FRAME_SIZE * 2);
        assertThat(stateOf(processor).leftoverCount).isZero();
        assertThat(stateOf(processor).leftoverBuffer).containsOnly((short) 0);
        assertThat(processor.flushLeftover()).isEmpty();
    }

    // 打断时残留样本必须丢弃且不产生帧，否则上一轮未成帧的尾音会拼进下一轮首帧
    @Test
    void discardLeftoverDropsRemainderWithoutEmittingFrame() {
        OpusProcessor processor = new OpusProcessor();
        processor.pcmToOpus(pcm(FRAME_SIZE + 40), true);
        assertThat(stateOf(processor).leftoverCount).isEqualTo(40);

        processor.discardLeftover();

        assertThat(stateOf(processor).leftoverCount).isZero();
        assertThat(stateOf(processor).leftoverBuffer).containsOnly((short) 0);
        // 丢弃之后再刷也不该有帧漏出
        assertThat(processor.flushLeftover()).isEmpty();
        // 下一轮首帧只包含新数据，不再被残留顶偏
        assertThat(processor.pcmToOpus(pcm(FRAME_SIZE), true)).hasSize(1);
        assertThat(stateOf(processor).leftoverCount).isZero();
    }

    @Test
    void flushLeftoverWithoutRemainderReturnsNoFrame() {
        assertThat(new OpusProcessor().flushLeftover()).isEmpty();
    }

    // 流式与非流式对同一段 PCM 必须编出完全相同的帧：
    // 两者不一致时，同一句话现场合成版与缓存命中版的起音会不一样
    @Test
    void streamingAndBatchEncodingProduceIdenticalFrames() {
        byte[] pcm = pcm(FRAME_SIZE * 2);

        List<byte[]> streamed = new OpusProcessor().pcmToOpus(pcm, true);
        List<byte[]> batched = new OpusProcessor().pcmToOpus(pcm, false);

        assertThat(streamed).hasSize(2);
        assertThat(batched).hasSize(2);
        assertThat(streamed.get(0)).isEqualTo(batched.get(0));
        assertThat(streamed.get(1)).isEqualTo(batched.get(1));
    }

    // 同一个编码器连续编两段相同 PCM，第二段不能因为段序不同而与第一段不同
    @Test
    void repeatedBatchCallsOnSameProcessorStayConsistent() {
        OpusProcessor processor = new OpusProcessor();

        List<byte[]> first = processor.pcmToOpus(pcm(FRAME_SIZE), false);
        List<byte[]> second = processor.pcmToOpus(pcm(FRAME_SIZE), false);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
    }

    @Test
    void oddLengthPcmDropsTrailingByte() {
        OpusProcessor processor = new OpusProcessor();
        byte[] odd = new byte[(FRAME_SIZE + 40) * 2 + 1];
        System.arraycopy(pcm(FRAME_SIZE + 40), 0, odd, 0, odd.length - 1);

        assertThat(processor.pcmToOpus(odd, true)).hasSize(1);
        // 多出来的半个样本被截掉，残留仍是 40 个完整样本
        assertThat(stateOf(processor).leftoverCount).isEqualTo(40);
    }

    @Test
    void emptyOrSingleBytePcmProducesNoFrames() {
        OpusProcessor processor = new OpusProcessor();

        assertThat(processor.pcmToOpus(null, true)).isEmpty();
        assertThat(processor.pcmToOpus(new byte[0], true)).isEmpty();
        assertThat(processor.pcmToOpus(new byte[1], true)).isEmpty();
        assertThat(stateOf(processor).leftoverCount).isZero();
    }

    @Test
    void silenceFrameIsCachedAndStableAcrossCalls() throws OpusException {
        byte[] first = OpusProcessor.silenceFrame();

        assertThat(OpusProcessor.silenceFrame()).isSameAs(first);
        assertThat(first).isNotEmpty();
        assertThat(first).isEqualTo(new OpusProcessor().pcmToOpus(new byte[FRAME_SIZE * 2], false).get(0));
        assertThat(new OpusProcessor().opusToPcm(first)).hasSize(FRAME_SIZE * 2);
    }
}

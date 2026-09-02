package com.xiaozhi.utils;

import io.github.jaredmdobson.concentus.OpusException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住流式编码的帧长不变量：下行音频、服务端 AEC 参考帧、句子边界补帧都按 60ms/960 样本一帧对齐，
 * 残留样本必须跨调用拼接而不是丢弃或补零，否则参考帧与设备播放点会错位。
 * 同时钉住淡入判定的当前真实行为——流式路径下淡入永不生效、非流式路径每次生效，与注释描述相反。
 */
class OpusProcessorStreamTest {

    private static final int FRAME_SIZE = AudioUtils.FRAME_SIZE;

    /** 生成指定样本数的16bit小端单声道PCM，幅度恒定以便淡入衰减可观测 */
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

    @Test
    void flushLeftoverWithoutRemainderReturnsNoFrame() {
        assertThat(new OpusProcessor().flushLeftover()).isEmpty();
    }

    // 当前行为：flushLeftover 只清残留样本，不复位 isFirst，收句后的下一段仍按"非首段"处理
    // 正确行为：flushLeftover 表示一段音频结束，应复位 isFirst 让下一段重新淡入
    // 生产代码 xiaozhi-common/src/main/java/com/xiaozhi/utils/OpusProcessor.java:100
    @Test
    void flushLeftoverDoesNotResetFirstSegmentFlag() {
        OpusProcessor processor = new OpusProcessor();
        processor.pcmToOpus(pcm(FRAME_SIZE + 40), true);

        processor.flushLeftover();

        assertThat(stateOf(processor).isFirst).isFalse();
    }

    // 当前行为：流式首次调用在淡入判定之前就把 isFirst 置 false，淡入分支在流式路径永不生效；
    //          非流式路径从不置 false，于是每一次调用的首帧都被重新淡入
    // 正确行为：淡入只应在每段音频的第一帧生效，且流式与非流式一致
    // 生产代码 xiaozhi-common/src/main/java/com/xiaozhi/utils/OpusProcessor.java:177
    @Test
    void fadeInIsSkippedWhenStreamingButReappliedOnEveryNonStreamCall() {
        byte[] pcm = pcm(FRAME_SIZE);
        OpusProcessor streaming = new OpusProcessor();
        OpusProcessor batch = new OpusProcessor();

        List<byte[]> streamed = streaming.pcmToOpus(pcm, true);
        List<byte[]> batched = batch.pcmToOpus(pcm, false);

        assertThat(streamed).hasSize(1);
        assertThat(batched).hasSize(1);
        // 同样的 PCM、同样设置的全新编码器，首帧不同只可能来自淡入
        assertThat(streamed.get(0)).isNotEqualTo(batched.get(0));
        assertThat(stateOf(streaming).isFirst).isFalse();
        assertThat(stateOf(batch).isFirst).isTrue();

        batch.pcmToOpus(pcm, false);
        assertThat(stateOf(batch).isFirst).isTrue();
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

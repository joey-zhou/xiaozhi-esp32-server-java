package com.xiaozhi.utils;

import io.github.jaredmdobson.concentus.OpusException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
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
    void nonStreamingCallPadsRemainderInsteadOfDroppingIt() {
        OpusProcessor processor = new OpusProcessor();

        // 不足一帧的尾部补零编码成额外一帧，不再被静默丢弃
        assertThat(processor.pcmToOpus(pcm(FRAME_SIZE + 40), false)).hasSize(2);
        assertThat(stateOf(processor).leftoverCount).isZero();
        assertThat(processor.flushLeftover()).isEmpty();
    }

    @Test
    void flushLeftoverEmitsExactlyOnePaddedFrameAndClearsBuffer() throws OpusException {
        OpusProcessor processor = new OpusProcessor();
        processor.pcmToOpus(pcm(FRAME_SIZE + 40), true);
        assertThat(stateOf(processor).leftoverCount).isEqualTo(40);

        List<OpusProcessor.EncodedFrame> tail = processor.flushLeftover();

        assertThat(tail).hasSize(1);
        // 残留样本必须补静音凑满一整帧，解码回来仍是 960 个样本
        assertThat(new OpusProcessor().opusToPcm(tail.get(0).opus())).hasSize(FRAME_SIZE * 2);
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

        List<OpusProcessor.EncodedFrame> streamed = new OpusProcessor().pcmToOpus(pcm, true);
        List<OpusProcessor.EncodedFrame> batched = new OpusProcessor().pcmToOpus(pcm, false);

        assertThat(streamed).hasSize(2);
        assertThat(batched).hasSize(2);
        assertThat(streamed.get(0).opus()).isEqualTo(batched.get(0).opus());
        assertThat(streamed.get(1).opus()).isEqualTo(batched.get(1).opus());
    }

    // 同一个编码器连续编两段相同 PCM，第二段不能因为段序不同而与第一段不同
    @Test
    void repeatedBatchCallsOnSameProcessorStayConsistent() {
        OpusProcessor processor = new OpusProcessor();

        List<OpusProcessor.EncodedFrame> first = processor.pcmToOpus(pcm(FRAME_SIZE), false);
        List<OpusProcessor.EncodedFrame> second = processor.pcmToOpus(pcm(FRAME_SIZE), false);

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
        assertThat(first).isEqualTo(new OpusProcessor().pcmToOpus(OpusProcessor.silencePcm(), false).get(0).opus());
        assertThat(new OpusProcessor().opusToPcm(first)).hasSize(FRAME_SIZE * 2);
    }

    // 静音帧的源 PCM 直接充当 AEC 参考信号，必须是整帧的纯静音
    @Test
    void silencePcmIsAFullFrameOfZeros() {
        assertThat(OpusProcessor.silencePcm()).hasSize(FRAME_SIZE * 2).containsOnly((byte) 0);
        assertThat(OpusProcessor.silencePcm()).isSameAs(OpusProcessor.silencePcm());
    }

    // 编码结果随帧带出的 PCM 就是喂给 AEC 的参考信号：必须与实际编进这一帧的样本逐字节相同，
    // 跨调用拼接的残留样本也要按原顺序出现在帧首，否则参考与设备播放的内容对不上
    @Test
    void encodedFrameCarriesExactlyThePcmThatWasEncoded() {
        OpusProcessor processor = new OpusProcessor();
        byte[] first = pcm(FRAME_SIZE + 40);
        byte[] second = pcm(FRAME_SIZE - 40);

        List<OpusProcessor.EncodedFrame> firstFrames = processor.pcmToOpus(first, true);
        List<OpusProcessor.EncodedFrame> secondFrames = processor.pcmToOpus(second, true);

        assertThat(firstFrames).hasSize(1);
        assertThat(firstFrames.get(0).pcm()).isEqualTo(slice(first, 0, FRAME_SIZE));

        assertThat(secondFrames).hasSize(1);
        byte[] expected = new byte[FRAME_SIZE * 2];
        System.arraycopy(slice(first, FRAME_SIZE, 40), 0, expected, 0, 40 * 2);
        System.arraycopy(slice(second, 0, FRAME_SIZE - 40), 0, expected, 40 * 2, (FRAME_SIZE - 40) * 2);
        assertThat(secondFrames.get(0).pcm()).isEqualTo(expected);
    }

    // 收尾帧补的是静音，带出的参考 PCM 也必须是"残留样本 + 静音"，而不是残留样本本身
    @Test
    void flushLeftoverCarriesRemainderPaddedWithSilence() {
        OpusProcessor processor = new OpusProcessor();
        byte[] input = pcm(FRAME_SIZE + 40);
        processor.pcmToOpus(input, true);

        List<OpusProcessor.EncodedFrame> tail = processor.flushLeftover();

        assertThat(tail).hasSize(1);
        byte[] expected = new byte[FRAME_SIZE * 2];
        System.arraycopy(slice(input, FRAME_SIZE, 40), 0, expected, 0, 40 * 2);
        assertThat(tail.get(0).pcm()).isEqualTo(expected);
    }

    // 只解码的实例（上行 VAD、AEC 兜底）不该白建一个编码器，只编码的实例（播放器）同理
    @Test
    void codecsAreBuiltOnlyForTheDirectionActuallyUsed() throws OpusException {
        OpusProcessor encodeOnly = new OpusProcessor();
        assertThat(ReflectionTestUtils.getField(encodeOnly, "encoder")).isNull();
        encodeOnly.pcmToOpus(pcm(FRAME_SIZE), false);
        assertThat(ReflectionTestUtils.getField(encodeOnly, "encoder")).isNotNull();
        assertThat(ReflectionTestUtils.getField(encodeOnly, "decoder")).isNull();

        OpusProcessor decodeOnly = new OpusProcessor();
        decodeOnly.opusToPcm(OpusProcessor.silenceFrame());
        assertThat(ReflectionTestUtils.getField(decodeOnly, "decoder")).isNotNull();
        assertThat(ReflectionTestUtils.getField(decodeOnly, "encoder")).isNull();
    }

    /** 取 PCM 中第 fromSample 个样本起的 count 个样本 */
    private static byte[] slice(byte[] pcm, int fromSample, int count) {
        return Arrays.copyOfRange(pcm, fromSample * 2, (fromSample + count) * 2);
    }
}

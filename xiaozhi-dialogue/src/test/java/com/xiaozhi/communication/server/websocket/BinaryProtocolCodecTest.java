package com.xiaozhi.communication.server.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设备与服务端约定的二进制帧格式：v1 裸 opus、v2 带 16 字节大端头（含播放时间戳）、v3 带 4 字节长度头。
 * 编解码错一个字节，设备说话服务端就听不见，或者时间戳对不齐导致 AEC 参考错位。
 */
class BinaryProtocolCodecTest {

    private static final byte[] OPUS = {0x11, 0x22, 0x33, 0x44};

    @Test
    void v1KeepsRawFrame() {
        assertThat(BinaryProtocolCodec.encode(1, OPUS, 12345L)).isEqualTo(OPUS);

        BinaryProtocolCodec.Frame frame = BinaryProtocolCodec.decode(1, OPUS);
        assertThat(frame.payload()).isEqualTo(OPUS);
        assertThat(frame.timestamp()).isZero();
    }

    @Test
    void v2WritesBigEndianHeader() {
        byte[] encoded = BinaryProtocolCodec.encode(2, OPUS, 0x01020304L);

        assertThat(encoded).hasSize(16 + OPUS.length);
        // version=2, type=0(opus), reserved=0
        assertThat(encoded[0]).isZero();
        assertThat(encoded[1]).isEqualTo((byte) 2);
        assertThat(encoded[2]).isZero();
        assertThat(encoded[3]).isZero();
        // timestamp 网络序大端
        assertThat(encoded[8]).isEqualTo((byte) 0x01);
        assertThat(encoded[9]).isEqualTo((byte) 0x02);
        assertThat(encoded[10]).isEqualTo((byte) 0x03);
        assertThat(encoded[11]).isEqualTo((byte) 0x04);
        // payload_size 大端
        assertThat(encoded[15]).isEqualTo((byte) OPUS.length);
        assertThat(Arrays.copyOfRange(encoded, 16, encoded.length)).isEqualTo(OPUS);
    }

    // 覆盖低32位的边界值：最高位为 1 时不能被解成负数
    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL})
    void v2RoundTripPreservesTimestamp(long timestamp) {
        BinaryProtocolCodec.Frame frame = BinaryProtocolCodec.decode(2, BinaryProtocolCodec.encode(2, OPUS, timestamp));

        assertThat(frame.payload()).isEqualTo(OPUS);
        assertThat(frame.timestamp()).isEqualTo(timestamp);
    }

    @Test
    void v3WritesBigEndianHeaderWithoutTimestamp() {
        byte[] encoded = BinaryProtocolCodec.encode(3, OPUS, 999L);

        assertThat(encoded).hasSize(4 + OPUS.length);
        assertThat(encoded[0]).isZero();
        assertThat(encoded[1]).isZero();
        assertThat(encoded[2]).isZero();
        assertThat(encoded[3]).isEqualTo((byte) OPUS.length);

        BinaryProtocolCodec.Frame frame = BinaryProtocolCodec.decode(3, encoded);
        assertThat(frame.payload()).isEqualTo(OPUS);
        assertThat(frame.timestamp()).isZero();
    }

    @Test
    void v2RejectsTruncatedHeader() {
        assertThat(BinaryProtocolCodec.decode(2, new byte[]{1, 2, 3})).isNull();
    }

    @Test
    void v2RejectsPayloadSizeBeyondFrame() {
        byte[] encoded = BinaryProtocolCodec.encode(2, OPUS, 1L);
        encoded[15] = (byte) 99;

        assertThat(BinaryProtocolCodec.decode(2, encoded)).isNull();
    }

    @Test
    void v2RejectsRawOpusFrame() {
        // 设备声明 v2 却发裸帧时必须能识别出来，交由调用方降级
        byte[] rawOpus = new byte[60];
        rawOpus[15] = (byte) 200;

        assertThat(BinaryProtocolCodec.decode(2, rawOpus)).isNull();
    }

    @Test
    void v3RejectsTruncatedAndOversizedFrames() {
        assertThat(BinaryProtocolCodec.decode(3, new byte[]{1, 2})).isNull();

        byte[] encoded = BinaryProtocolCodec.encode(3, OPUS, 0L);
        encoded[3] = (byte) 99;
        assertThat(BinaryProtocolCodec.decode(3, encoded)).isNull();
    }

    @Test
    void unknownVersionFallsBackToRawFrame() {
        assertThat(BinaryProtocolCodec.encode(9, OPUS, 1L)).isEqualTo(OPUS);
        assertThat(BinaryProtocolCodec.decode(9, OPUS).payload()).isEqualTo(OPUS);
    }

    @Test
    void isSupportedAcceptsOnlyKnownVersions() {
        assertThat(BinaryProtocolCodec.isSupported(1)).isTrue();
        assertThat(BinaryProtocolCodec.isSupported(2)).isTrue();
        assertThat(BinaryProtocolCodec.isSupported(3)).isTrue();
        assertThat(BinaryProtocolCodec.isSupported(0)).isFalse();
        assertThat(BinaryProtocolCodec.isSupported(4)).isFalse();
    }
}

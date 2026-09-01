package com.xiaozhi.communication.server.websocket;

import java.nio.ByteBuffer;

/**
 * 设备 WebSocket 二进制帧编解码，收发格式对称。所有多字节字段为网络序大端。
 *
 * v1：裸 Opus，无帧头
 * v2：16 字节头 [version:2][type:2][reserved:4][timestamp:4][payloadSize:4]，timestamp 供服务端 AEC 对齐
 * v3：4 字节头 [type:1][reserved:1][payloadSize:2]，无 timestamp
 */
public final class BinaryProtocolCodec {

    public static final int VERSION_V1 = 1;
    public static final int VERSION_V2 = 2;
    public static final int VERSION_V3 = 3;

    private static final int HEADER_SIZE_V2 = 16;
    private static final int HEADER_SIZE_V3 = 4;
    private static final int TYPE_OPUS = 0;

    private BinaryProtocolCodec() {
    }

    public static boolean isSupported(int version) {
        return version == VERSION_V1 || version == VERSION_V2 || version == VERSION_V3;
    }

    /**
     * @param timestamp 毫秒时间戳，仅 v2 承载
     */
    public static byte[] encode(int version, byte[] payload, long timestamp) {
        return switch (version) {
            case VERSION_V2 -> ByteBuffer.allocate(HEADER_SIZE_V2 + payload.length)
                    .putShort((short) VERSION_V2)
                    .putShort((short) TYPE_OPUS)
                    .putInt(0)
                    .putInt((int) timestamp)
                    .putInt(payload.length)
                    .put(payload)
                    .array();
            case VERSION_V3 -> ByteBuffer.allocate(HEADER_SIZE_V3 + payload.length)
                    .put((byte) TYPE_OPUS)
                    .put((byte) 0)
                    .putShort((short) payload.length)
                    .put(payload)
                    .array();
            default -> payload;
        };
    }

    /**
     * 按指定版本解帧，帧头与实际长度不自洽时返回 null（交由调用方按 v1 兜底并降级会话版本）。
     */
    public static Frame decode(int version, byte[] data) {
        return switch (version) {
            case VERSION_V2 -> {
                if (data.length < HEADER_SIZE_V2) {
                    yield null;
                }
                ByteBuffer buf = ByteBuffer.wrap(data);
                buf.position(8);
                long timestamp = buf.getInt() & 0xFFFFFFFFL;
                int payloadSize = buf.getInt();
                if (payloadSize < 0 || payloadSize > data.length - HEADER_SIZE_V2) {
                    yield null;
                }
                byte[] payload = new byte[payloadSize];
                buf.get(payload);
                yield new Frame(payload, timestamp);
            }
            case VERSION_V3 -> {
                if (data.length < HEADER_SIZE_V3) {
                    yield null;
                }
                ByteBuffer buf = ByteBuffer.wrap(data);
                buf.position(2);
                int payloadSize = buf.getShort() & 0xFFFF;
                if (payloadSize > data.length - HEADER_SIZE_V3) {
                    yield null;
                }
                byte[] payload = new byte[payloadSize];
                buf.get(payload);
                yield new Frame(payload, 0);
            }
            default -> new Frame(data, 0);
        };
    }

    public record Frame(byte[] payload, long timestamp) {
    }
}

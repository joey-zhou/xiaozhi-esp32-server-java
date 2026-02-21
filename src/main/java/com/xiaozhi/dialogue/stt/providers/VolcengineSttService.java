package com.xiaozhi.dialogue.stt.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.entity.SysConfig;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;

/**
 * 火山引擎大模型流式语音识别服务
 * 基于 WebSocket 二进制协议实现
 * 
 * @see <a href="https://www.volcengine.com/docs/6561/1354869">大模型流式语音识别API</a>
 */
public class VolcengineSttService implements SttService {
    private static final Logger logger = LoggerFactory.getLogger(VolcengineSttService.class);
    private static final String PROVIDER_NAME = "volcengine";

    // WebSocket API地址
    private static final String WS_API_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel";

    // 识别超时时间（90秒）
    private static final long RECOGNITION_TIMEOUT_MS = 90000;
    // 队列等待超时时间
    private static final int QUEUE_TIMEOUT_MS = 100;

    // 协议常量
    private static final byte PROTOCOL_VERSION = 0b0001;
    private static final byte HEADER_SIZE = 0b0001;
    private static final byte FULL_CLIENT_REQUEST = 0b0001;
    private static final byte AUDIO_ONLY_REQUEST = 0b0010;
    private static final byte FULL_SERVER_RESPONSE = (byte) 0b1001;
    private static final byte SERVER_ERROR_RESPONSE = (byte) 0b1111;
    private static final byte JSON_SERIALIZATION = 0b0001;
    private static final byte GZIP_COMPRESSION = 0b0001;
    private static final byte NO_SEQUENCE = 0b0000;
    private static final byte LAST_PACKET = 0b0010;

    private final String appId;
    private final String accessToken;
    private final String resourceId;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client;

    public VolcengineSttService(SysConfig config) {
        this.appId = config.getAppId();
        this.accessToken = config.getApiKey();
        // 固定使用豆包流式语音识别模型1.0小时版
        this.resourceId = "volc.bigasr.sauc.duration";

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String recognition(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            logger.warn("音频数据为空！");
            return null;
        }

        // 对于非流式识别，将音频数据分块构造为Flux，然后走流式接口
        Flux<byte[]> audioFlux = Flux.create(sink -> {
            Thread.startVirtualThread(() -> {
                try {
                    // 分块发送音频数据，每次发送 3200 字节（100ms 的 16kHz 16bit 音频）
                    int chunkSize = 3200;
                    for (int i = 0; i < audioData.length; i += chunkSize) {
                        int end = Math.min(i + chunkSize, audioData.length);
                        byte[] chunk = new byte[end - i];
                        System.arraycopy(audioData, i, chunk, 0, end - i);
                        sink.next(chunk);
                    }
                } catch (Exception e) {
                    logger.error("发送音频数据时发生错误", e);
                } finally {
                    sink.complete();
                }
            });
        });

        return streamRecognition(audioFlux);
    }

    @Override
    public String streamRecognition(Flux<byte[]> audioFlux) {
        // 检查配置是否已设置
        if (appId == null || accessToken == null) {
            logger.error("火山引擎语音识别配置未设置，无法进行识别");
            return null;
        }

        String connectId = UUID.randomUUID().toString();
        AtomicReference<String> finalResult = new AtomicReference<>("");
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        AtomicBoolean latchReleased = new AtomicBoolean(false);
        CountDownLatch recognitionLatch = new CountDownLatch(1);
        BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
        AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();

        // 订阅音频流
        audioFlux.subscribe(
                data -> audioQueue.offer(data),
                error -> {
                    logger.error("音频流处理错误", error);
                    isCompleted.set(true);
                },
                () -> isCompleted.set(true)
        );

        // 构建请求
        Request request = new Request.Builder()
                .url(WS_API_URL)
                .addHeader("X-Api-App-Key", appId)
                .addHeader("X-Api-Access-Key", accessToken)
                .addHeader("X-Api-Resource-Id", resourceId)
                .addHeader("X-Api-Connect-Id", connectId)
                .build();

        client.newWebSocket(request, new WebSocketListener() {
            private final StringBuilder textBuilder = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocketRef.set(webSocket);

                // 发送 full client request
                try {
                    byte[] fullRequest = buildFullClientRequest();
                    webSocket.send(okio.ByteString.of(fullRequest));
                } catch (Exception e) {
                    logger.error("发送 full client request 失败", e);
                    webSocket.close(1000, "发送请求失败");
                }

                // 启动虚拟线程发送音频数据
                Thread.startVirtualThread(() -> {
                    try {
                        while (!isCompleted.get() || !audioQueue.isEmpty()) {
                            byte[] audioChunk = audioQueue.poll(QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                            if (audioChunk != null && audioChunk.length > 0) {
                                try {
                                    byte[] audioRequest = buildAudioRequest(audioChunk, false);
                                    webSocket.send(okio.ByteString.of(audioRequest));
                                } catch (Exception e) {
                                    logger.error("发送音频数据时发生错误", e);
                                    break;
                                }
                            }
                        }

                        // 发送最后一包（空音频，标记结束）
                        try {
                            byte[] lastRequest = buildAudioRequest(new byte[0], true);
                            webSocket.send(okio.ByteString.of(lastRequest));
                        } catch (Exception e) {
                            logger.error("发送最后一包时发生错误", e);
                        }
                    } catch (Exception e) {
                        logger.error("处理音频流时发生错误", e);
                    }
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                try {
                    parseServerResponse(bytes.toByteArray(), textBuilder, finalResult, recognitionLatch, latchReleased);
                } catch (Exception e) {
                    logger.error("解析服务器响应失败", e);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("火山引擎识别失败", t);
                if (latchReleased.compareAndSet(false, true)) {
                    recognitionLatch.countDown();
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (latchReleased.compareAndSet(false, true)) {
                    recognitionLatch.countDown();
                }
            }
        });

        try {
            // 等待识别完成或超时
            boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!recognized) {
                logger.warn("火山引擎识别超时 - ConnectId: {}", connectId);
            }
        } catch (InterruptedException e) {
            logger.error("等待识别结果时被中断", e);
            Thread.currentThread().interrupt();
        } finally {
            // 确保关闭 WebSocket 连接
            WebSocket ws = webSocketRef.get();
            if (ws != null) {
                ws.close(1000, "识别完成");
            }
        }

        return finalResult.get();
    }

    /**
     * 构建 full client request 消息
     */
    private byte[] buildFullClientRequest() throws Exception {
        // 构建请求JSON
        ObjectNode requestJson = objectMapper.createObjectNode();

        // user 配置
        ObjectNode user = objectMapper.createObjectNode();
        user.put("uid", "xiaozhi-" + UUID.randomUUID().toString().substring(0, 8));
        requestJson.set("user", user);

        // audio 配置
        ObjectNode audio = objectMapper.createObjectNode();
        audio.put("format", "pcm");
        audio.put("codec", "raw");
        audio.put("rate", 16000);
        audio.put("bits", 16);
        audio.put("channel", 1);
        requestJson.set("audio", audio);

        // request 配置
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model_name", "bigmodel");
        request.put("enable_itn", true);
        request.put("enable_punc", true);
        request.put("enable_ddc", false);
        request.put("show_utterances", true);
        request.put("result_type", "full");
        requestJson.set("request", request);

        String jsonStr = objectMapper.writeValueAsString(requestJson);
        byte[] jsonBytes = jsonStr.getBytes("UTF-8");

        // Gzip 压缩
        byte[] compressedPayload = gzipCompress(jsonBytes);

        // 构建二进制消息
        return buildBinaryMessage(FULL_CLIENT_REQUEST, NO_SEQUENCE, JSON_SERIALIZATION, GZIP_COMPRESSION, compressedPayload);
    }

    /**
     * 构建 audio only request 消息
     */
    private byte[] buildAudioRequest(byte[] audioData, boolean isLast) throws Exception {
        // Gzip 压缩音频数据
        byte[] compressedPayload = gzipCompress(audioData);

        byte flags = isLast ? LAST_PACKET : NO_SEQUENCE;

        // 构建二进制消息
        return buildBinaryMessage(AUDIO_ONLY_REQUEST, flags, (byte) 0b0000, GZIP_COMPRESSION, compressedPayload);
    }

    /**
     * 构建二进制消息
     */
    private byte[] buildBinaryMessage(byte messageType, byte flags, byte serialization, byte compression, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + payload.length);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // Header (4 bytes)
        byte byte0 = (byte) ((PROTOCOL_VERSION << 4) | HEADER_SIZE);
        byte byte1 = (byte) ((messageType << 4) | flags);
        byte byte2 = (byte) ((serialization << 4) | compression);
        byte byte3 = 0x00; // Reserved

        buffer.put(byte0);
        buffer.put(byte1);
        buffer.put(byte2);
        buffer.put(byte3);

        // Payload size (4 bytes, big-endian)
        buffer.putInt(payload.length);

        // Payload
        buffer.put(payload);

        return buffer.array();
    }

    /**
     * 解析服务器响应
     */
    private void parseServerResponse(byte[] data, StringBuilder textBuilder, 
            AtomicReference<String> finalResult, CountDownLatch latch, AtomicBoolean latchReleased) throws Exception {
        if (data.length < 4) {
            logger.warn("响应数据过短");
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // 解析 header (4 bytes)
        buffer.get(); // byte0: protocol version & header size, 跳过
        byte byte1 = buffer.get();
        byte byte2 = buffer.get();
        buffer.get(); // Reserved byte, 跳过

        // 解析各字段 (仅使用需要的字段)
        int messageType = (byte1 >> 4) & 0x0F;
        int flags = byte1 & 0x0F;
        int compression = byte2 & 0x0F;

        // 检查是否有 sequence number（flags 包含 0b0001 或 0b0011）
        boolean hasSequence = (flags & 0b0001) != 0;
        if (hasSequence && buffer.remaining() >= 4) {
            buffer.getInt(); // 读取并跳过 sequence number
        }

        // 检查消息类型
        if (messageType == (SERVER_ERROR_RESPONSE & 0x0F)) {
            // 错误消息
            if (buffer.remaining() >= 8) {
                int errorCode = buffer.getInt();
                int errorMsgSize = buffer.getInt();
                if (buffer.remaining() >= errorMsgSize) {
                    byte[] errorMsgBytes = new byte[errorMsgSize];
                    buffer.get(errorMsgBytes);
                    String errorMsg = new String(errorMsgBytes, "UTF-8");
                    logger.error("火山引擎识别错误 - Code: {}, Message: {}", errorCode, errorMsg);
                }
            }
            if (latchReleased.compareAndSet(false, true)) {
                latch.countDown();
            }
            return;
        }

        if (messageType != (FULL_SERVER_RESPONSE & 0x0F)) {
            return;
        }

        // 读取 payload
        if (buffer.remaining() < 4) {
            return;
        }

        int payloadSize = buffer.getInt();
        if (buffer.remaining() < payloadSize) {
            logger.warn("Payload 数据不完整");
            return;
        }

        byte[] payload = new byte[payloadSize];
        buffer.get(payload);

        // 解压缩
        byte[] decompressedPayload;
        if (compression == GZIP_COMPRESSION) {
            decompressedPayload = gzipDecompress(payload);
        } else {
            decompressedPayload = payload;
        }

        // 解析 JSON
        String jsonStr = new String(decompressedPayload, "UTF-8");
        JsonNode responseJson = objectMapper.readTree(jsonStr);

        // 提取识别结果
        if (responseJson.has("result")) {
            JsonNode result = responseJson.get("result");
            if (result.has("text")) {
                String text = result.get("text").asText();
                if (text != null && !text.isEmpty()) {
                    synchronized (textBuilder) {
                        textBuilder.setLength(0);
                        textBuilder.append(text);
                    }
                    finalResult.set(text);
                }
            }

        }

        // 检查是否是最后一包响应（flags 包含 0b0010 或 0b0011）
        boolean isLast = (flags & 0b0010) != 0;
        if (isLast) {
            if (latchReleased.compareAndSet(false, true)) {
                latch.countDown();
            }
        }
    }

    /**
     * Gzip 压缩
     */
    private byte[] gzipCompress(byte[] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }

    /**
     * Gzip 解压缩
     */
    private byte[] gzipDecompress(byte[] data) throws Exception {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(bis)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
        }
        return bos.toByteArray();
    }
}

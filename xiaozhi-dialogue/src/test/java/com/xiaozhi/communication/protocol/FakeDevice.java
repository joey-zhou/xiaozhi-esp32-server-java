package com.xiaozhi.communication.protocol;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.server.websocket.BinaryProtocolCodec;
import com.xiaozhi.enums.ListenMode;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 设备端 DSL：把「设备发了什么」写成一行调用，出站消息统一从 {@link #transport()} 读。
 *
 * <p>所有 send 系方法在调用线程上同步走完 WebSocketHandler，返回时服务端的同步部分已执行完；
 * 虚拟线程里的部分（STT、状态写库、MCP 初始化）仍需用 {@link AwaitHelper} 等。
 *
 * <p>{@link #speak(byte...)} 例外：遇到 SPEECH_START 标记会等到 STT 已开始订阅本轮音频流再发下一帧，
 * 保证「首帧进流」与后续帧的相对顺序确定，用例可以直接断言帧序列。
 */
class FakeDevice {

    private final ProtocolTestHarness harness;
    private final String deviceId;
    private final FakeWebSocketTransport transport;

    FakeDevice(ProtocolTestHarness harness, String deviceId, FakeWebSocketTransport transport) {
        this.harness = harness;
        this.deviceId = deviceId;
        this.transport = transport;
    }

    String deviceId() {
        return deviceId;
    }

    String sessionId() {
        return transport.getId();
    }

    FakeWebSocketTransport transport() {
        return transport;
    }

    /** 服务端为本连接注册的会话，连接被拒时为 null */
    ChatSession session() {
        return harness.sessionManager().getSession(sessionId());
    }

    // ========== 上行文本 ==========

    /** 默认 hello：v1 裸帧、不开 mcp、不要服务端 aec、声明服务端同款音频参数 */
    FakeDevice hello() {
        return hello(1, false, false);
    }

    FakeDevice hello(Integer version) {
        return hello(version, false, false);
    }

    FakeDevice hello(Integer version, boolean mcp, boolean aec) {
        String versionField = version == null ? "" : "\"version\":" + version + ",";
        return sendText("{\"type\":\"hello\"," + versionField
                + "\"transport\":\"websocket\","
                + "\"features\":{\"mcp\":" + mcp + ",\"aec\":" + aec + "},"
                + "\"audio_params\":{\"format\":\"opus\",\"sample_rate\":16000,"
                + "\"channels\":1,\"frame_duration\":60}}");
    }

    FakeDevice listenStart(ListenMode mode) {
        return sendText("{\"type\":\"listen\",\"state\":\"start\",\"mode\":\"" + mode.getValue() + "\"}");
    }

    /** listen/stop 报文不带 mode，与真实设备一致 */
    FakeDevice listenStop() {
        return sendText("{\"type\":\"listen\",\"state\":\"stop\"}");
    }

    FakeDevice listenDetect(String text) {
        return sendText("{\"type\":\"listen\",\"state\":\"detect\",\"text\":\"" + text + "\"}");
    }

    FakeDevice listenText(String text) {
        return sendText("{\"type\":\"listen\",\"state\":\"text\",\"text\":\"" + text + "\"}");
    }

    FakeDevice abort(String reason) {
        return sendText("{\"type\":\"abort\",\"reason\":\"" + reason + "\"}");
    }

    FakeDevice goodbye() {
        return sendText("{\"type\":\"goodbye\"}");
    }

    /** descriptors / states 传 null 表示本条报文不带该字段 */
    FakeDevice iot(String descriptorsJson, String statesJson) {
        StringBuilder json = new StringBuilder("{\"type\":\"iot\"");
        if (descriptorsJson != null) {
            json.append(",\"descriptors\":").append(descriptorsJson);
        }
        if (statesJson != null) {
            json.append(",\"states\":").append(statesJson);
        }
        return sendText(json.append("}").toString());
    }

    FakeDevice mcpReply(long id, String resultJson) {
        return sendText("{\"type\":\"mcp\",\"payload\":{\"jsonrpc\":\"2.0\",\"id\":" + id
                + ",\"result\":" + resultJson + "}}");
    }

    /** 原样发一条文本帧，畸形报文与未知类型走这个 */
    FakeDevice sendText(String rawPayload) {
        deliver(new TextMessage(rawPayload));
        return this;
    }

    // ========== 上行二进制 ==========

    /** 按会话当前协商的协议版本编码后发出 */
    FakeDevice sendAudio(byte[] payload) {
        return sendAudio(payload, 0L);
    }

    FakeDevice sendAudio(byte[] payload, long timestamp) {
        ChatSession session = session();
        int version = session != null ? session.getProtocolVersion() : BinaryProtocolCodec.VERSION_V1;
        return sendRawAudio(BinaryProtocolCodec.encode(version, payload, timestamp));
    }

    /** 不做任何编码，原样发出，用于构造与声明版本不符的帧 */
    FakeDevice sendRawAudio(byte[] frame) {
        deliver(new BinaryMessage(ByteBuffer.wrap(frame)));
        return this;
    }

    /** 走容器的统一入口 handleMessage，由它分发到 handleTextMessage / handleBinaryMessage */
    private void deliver(WebSocketMessage<?> message) {
        try {
            harness.webSocketHandler().handleMessage(transport, message);
        } catch (Exception e) {
            throw new IllegalStateException("投递上行消息失败", e);
        }
    }

    /**
     * 按 VAD 脚本发一串音频帧并等本轮识别启动，标记取 {@link ScriptedVadService} 的常量。
     * 例：{@code speak(SPEECH_START, SPEECH_CONTINUE, SPEECH_END)}
     *
     * <p>遇到 SPEECH_START 会等到 STT 已订阅本轮音频流才发下一帧，所以只能用在
     * 「服务端确实会起识别」的场景。未绑定设备、VAD 未初始化（唤醒词前置缓冲）、
     * 告别语播放中这类音频被丢弃的场景要改用 {@link #sendFrames(byte...)}，否则会等超时。
     */
    FakeDevice speak(byte... vadScript) {
        for (byte marker : vadScript) {
            int streamsBefore = harness.stt().streamCalls();
            sendAudio(frame(marker));
            if (marker == ScriptedVadService.SPEECH_START) {
                AwaitHelper.until("STT 已开始订阅本轮音频流",
                        () -> harness.stt().streamCalls() > streamsBefore);
            }
        }
        return this;
    }

    /** 只发帧不做任何同步，用于音频预期被丢弃或只进唤醒词缓冲的场景 */
    FakeDevice sendFrames(byte... vadScript) {
        for (byte marker : vadScript) {
            sendAudio(frame(marker));
        }
        return this;
    }

    /** 构造一个带脚本标记的假 opus 帧，首字节是标记，其余是随机填充 */
    static byte[] frame(byte vadMarker) {
        byte[] payload = new byte[60];
        ThreadLocalRandom.current().nextBytes(payload);
        payload[0] = vadMarker;
        return payload;
    }

    // ========== 断链 ==========

    /** 设备正常断开：先关传输再回调 afterConnectionClosed，与容器行为一致 */
    FakeDevice disconnect() {
        return disconnect(CloseStatus.NORMAL);
    }

    FakeDevice disconnect(CloseStatus status) {
        transport.close(status);
        harness.webSocketHandler().afterConnectionClosed(transport, status);
        return this;
    }
}

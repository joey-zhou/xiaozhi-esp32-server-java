package com.xiaozhi.communication.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.communication.server.websocket.BinaryProtocolCodec;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 设备侧 WebSocket 传输层假体，实现 Spring 的 {@link WebSocketSession}，不开端口不起容器。
 *
 * <p>提供三件真实传输才有、Mockito 替身给不了的能力：
 * <ul>
 *   <li>按到达顺序记录出站消息，文本与二进制混在同一条流水里（{@link #outbound()}），
 *       也可分别取（{@link #textMessages()} / {@link #binaryFrames()}）；</li>
 *   <li>可翻转的 {@link #isOpen()}，生产代码里 {@code MessageSender.sendTtsMessage}
 *       与 {@code SessionManager.closeSession} 都依赖它；</li>
 *   <li>记录 {@link #closeStatus()}，可断言服务端用什么 CloseStatus 关的连接。</li>
 * </ul>
 *
 * <p>假体模拟的行为，用例不要按真实容器的语义去断言：
 * <ul>
 *   <li>连接关闭后再发消息不抛异常，只计入 {@link #droppedAfterClose()}；真实 Tomcat 会抛 IOException；</li>
 *   <li>没有消息大小上限、没有分片、没有背压，setXxxMessageSizeLimit 只是记值；</li>
 *   <li>sendMessage 在调用线程上同步完成，不存在真实链路的发送队列与网络延迟。</li>
 * </ul>
 */
class FakeWebSocketTransport implements WebSocketSession {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String id;
    private final HttpHeaders handshakeHeaders = new HttpHeaders();
    private final Map<String, Object> attributes = new HashMap<>();
    private final List<Sent> outbound = new CopyOnWriteArrayList<>();

    private volatile URI uri = URI.create("ws://example.com/ws/xiaozhi/v1/");
    private volatile boolean open = true;
    private volatile CloseStatus closeStatus;
    private volatile int droppedAfterClose;
    private volatile int textMessageSizeLimit = 65536;
    private volatile int binaryMessageSizeLimit = 65536;
    /** 发文本消息时的同步回调，用于在"消息刚发出那一刻"回查服务端状态 */
    private volatile Consumer<String> onTextSent;

    FakeWebSocketTransport(String id) {
        this.id = id;
    }

    /** 出站消息流水的一条记录，binary 为 true 时取 payload，否则取 text */
    record Sent(boolean binary, String text, byte[] payload) {
    }

    // ========== 编排 ==========

    FakeWebSocketTransport withHandshakeHeader(String name, String value) {
        handshakeHeaders.set(name, value);
        return this;
    }

    FakeWebSocketTransport withUri(String uri) {
        this.uri = URI.create(uri);
        return this;
    }

    /** 模拟对端掉线：连接不再可用，但不产生 CloseStatus */
    void breakConnection() {
        this.open = false;
    }

    void onTextSent(Consumer<String> hook) {
        this.onTextSent = hook;
    }

    // ========== 出站读取 ==========

    List<Sent> outbound() {
        return List.copyOf(outbound);
    }

    List<String> textMessages() {
        return outbound.stream().filter(s -> !s.binary()).map(Sent::text).toList();
    }

    List<JsonNode> jsonMessages() {
        return textMessages().stream().map(FakeWebSocketTransport::parse).toList();
    }

    /**
     * 出站文本消息的有序签名，形如 {@code hello}、{@code tts:start}、{@code stt}。
     * 断言消息顺序时配合 AssertJ 的 containsSubsequence / containsExactly 使用。
     */
    List<String> jsonSignatures() {
        return jsonMessages().stream().map(FakeWebSocketTransport::signature).toList();
    }

    /** 首条匹配签名的 JSON 消息，签名规则同 {@link #jsonSignatures()} */
    Optional<JsonNode> firstJson(String signature) {
        return jsonMessages().stream().filter(n -> signature.equals(signature(n))).findFirst();
    }

    /** 轮询等待某个签名的消息出站并返回它，超时抛 AssertionError */
    JsonNode awaitJson(String signature) {
        AwaitHelper.until("出站消息 " + signature, () -> firstJson(signature).isPresent());
        return firstJson(signature).orElseThrow();
    }

    /** 下行二进制帧的原始字节，含协议帧头 */
    List<byte[]> binaryFrames() {
        return outbound.stream().filter(Sent::binary).map(Sent::payload).toList();
    }

    /** 按指定协议版本解出的下行 opus 负载 */
    List<byte[]> binaryPayloads(int protocolVersion) {
        return binaryFrames().stream()
                .map(f -> BinaryProtocolCodec.decode(protocolVersion, f))
                .filter(java.util.Objects::nonNull)
                .map(BinaryProtocolCodec.Frame::payload)
                .toList();
    }

    /** 按指定协议版本解出的下行帧时间戳，v1/v3 恒为 0 */
    List<Long> binaryTimestamps(int protocolVersion) {
        return binaryFrames().stream()
                .map(f -> BinaryProtocolCodec.decode(protocolVersion, f))
                .filter(java.util.Objects::nonNull)
                .map(BinaryProtocolCodec.Frame::timestamp)
                .toList();
    }

    CloseStatus closeStatus() {
        return closeStatus;
    }

    int droppedAfterClose() {
        return droppedAfterClose;
    }

    void clearOutbound() {
        outbound.clear();
    }

    private static JsonNode parse(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("出站消息不是合法 JSON: " + text, e);
        }
    }

    private static String signature(JsonNode node) {
        String type = node.path("type").asText("");
        String state = node.path("state").asText("");
        return state.isEmpty() ? type : type + ":" + state;
    }

    // ========== WebSocketSession ==========

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        return handshakeHeaders;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return new InetSocketAddress("127.0.0.1", 8091);
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return new InetSocketAddress("127.0.0.1", 54321);
    }

    @Override
    public String getAcceptedProtocol() {
        return null;
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
        this.textMessageSizeLimit = messageSizeLimit;
    }

    @Override
    public int getTextMessageSizeLimit() {
        return textMessageSizeLimit;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        this.binaryMessageSizeLimit = messageSizeLimit;
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return binaryMessageSizeLimit;
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return List.of();
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) {
        if (!open) {
            droppedAfterClose++;
            return;
        }
        if (message instanceof TextMessage text) {
            outbound.add(new Sent(false, text.getPayload(), null));
            Consumer<String> hook = onTextSent;
            if (hook != null) {
                hook.accept(text.getPayload());
            }
        } else if (message instanceof BinaryMessage binary) {
            ByteBuffer buffer = binary.getPayload();
            byte[] copy = new byte[buffer.remaining()];
            buffer.duplicate().get(copy);
            outbound.add(new Sent(true, null, copy));
        } else {
            throw new IllegalArgumentException("未预期的消息类型: " + message.getClass());
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        close(CloseStatus.NORMAL);
    }

    @Override
    public void close(CloseStatus status) {
        if (closeStatus == null) {
            closeStatus = status;
        }
        open = false;
    }

    /** 供需要多次断言的用例复制一份快照，避免边断言边变化 */
    List<Sent> snapshot() {
        return new ArrayList<>(outbound);
    }
}

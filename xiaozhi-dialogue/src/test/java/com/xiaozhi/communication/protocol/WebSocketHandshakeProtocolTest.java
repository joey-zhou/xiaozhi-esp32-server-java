package com.xiaozhi.communication.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.InstanceIdHolder;
import com.xiaozhi.communication.domain.AudioParams;
import com.xiaozhi.communication.server.websocket.BinaryProtocolCodec;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.enums.ListenMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉 WebSocket 握手阶段的协议不变量：
 * <ul>
 *   <li>hello 响应回显的是本连接的 sessionId 与服务端固定音频参数，不是设备声明的参数；</li>
 *   <li>hello 是幂等的补充声明而不是会话重置——重复 hello 不换会话、不停播放；</li>
 *   <li>features.aec 的取值每次 hello 都会重新落到 AEC 会话上（拆除也算生效）；</li>
 *   <li>没有可用的设备标识就必须以 BAD_DATA 关闭，不能建出无主会话；</li>
 *   <li>设备标识只认 device-id 一个键，允许从 URI query 取，与握手鉴权拦截器认定一致；</li>
 *   <li>本项目不强制 hello 前置，listen 先到也照常进入聆听（与 Go 版实现不同）；</li>
 *   <li>设备曾绑定在别的实例时，连接建立要广播清理旧实例上的幽灵会话，且不误伤本实例。</li>
 * </ul>
 */
class WebSocketHandshakeProtocolTest {

    private static final String DEVICE_ID = "94:a9:90:2b:dd:18";
    private static final String OTHER_DEVICE_ID = "94:a9:90:2b:dd:19";

    /** 播放帧的首字节标记，用于把 TTS 真帧与播放器补的静音帧区分开 */
    private static final byte REPLY_FRAME_MARKER = (byte) 0xAB;
    private static final int REPLY_FRAME_LENGTH = 60;

    /** 设备声明 24k / 双声道 / 20ms，与服务端处理链路（16k/单声道/60ms）不一致 */
    private static final String HELLO_V2_WITH_FOREIGN_AUDIO_PARAMS = "{\"type\":\"hello\",\"version\":2,"
            + "\"transport\":\"websocket\",\"features\":{\"mcp\":false,\"aec\":false},"
            + "\"audio_params\":{\"format\":\"opus\",\"sample_rate\":24000,"
            + "\"channels\":2,\"frame_duration\":20}}";

    /** 完全不带 features 字段的 hello，等价于设备声明「我自己已消回声」 */
    private static final String HELLO_WITHOUT_FEATURES = "{\"type\":\"hello\",\"version\":1,"
            + "\"transport\":\"websocket\","
            + "\"audio_params\":{\"format\":\"opus\",\"sample_rate\":16000,"
            + "\"channels\":1,\"frame_duration\":60}}";

    private ProtocolTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = ProtocolTestHarness.create();
    }

    @AfterEach
    void tearDown() {
        harness.shutdown();
    }

    @Test
    void helloEchoesServerAudioParamsAndSessionId() {
        FakeDevice device = harness.connect(DEVICE_ID);

        device.sendText(HELLO_V2_WITH_FOREIGN_AUDIO_PARAMS);

        JsonNode hello = device.transport().awaitJson("hello");
        // 握手阶段只该有这一条出站文本
        assertThat(device.transport().jsonSignatures()).containsExactly("hello");
        assertThat(hello.path("session_id").asText()).isEqualTo(device.sessionId());
        assertThat(hello.path("transport").asText()).isEqualTo("websocket");
        assertThat(hello.path("version").asInt()).isEqualTo(BinaryProtocolCodec.VERSION_V2);
        // 回显的是服务端处理链路固定的参数，设备声明的 24k/2ch/20ms 一律不回传
        JsonNode audioParams = hello.path("audio_params");
        assertThat(audioParams.path("format").asText()).isEqualTo(AudioParams.SERVER_FORMAT);
        assertThat(audioParams.path("sample_rate").asInt()).isEqualTo(AudioParams.SERVER_SAMPLE_RATE);
        assertThat(audioParams.path("channels").asInt()).isEqualTo(AudioParams.SERVER_CHANNELS);
        assertThat(audioParams.path("frame_duration").asInt()).isEqualTo(AudioParams.SERVER_FRAME_DURATION);
        // 设备声明的版本落到会话上，收发共用
        assertThat(device.session().getProtocolVersion()).isEqualTo(BinaryProtocolCodec.VERSION_V2);
        assertThat(device.transport().binaryFrames()).isEmpty();
    }

    @Test
    void repeatedHelloDoesNotResetSessionOrStopPlayback() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        device.transport().awaitJson("hello");
        ChatSession sessionBeforeSecondHello = device.session();

        startPlayback(device, 20);
        AwaitHelper.until("下行音频已开始", () -> !device.transport().binaryFrames().isEmpty());

        // 播放途中设备又发一次 hello
        device.hello();
        AwaitHelper.until("第二条 hello 已回", () -> helloCount(device) == 2);
        int framesAtSecondHello = device.transport().binaryFrames().size();
        // 反向断言前先等一个后置信号：hello 之后播放仍在推进
        AwaitHelper.until("第二条 hello 之后仍在下发音频",
                () -> device.transport().binaryFrames().size() > framesAtSecondHello);

        List<JsonNode> hellos = device.transport().jsonMessages().stream()
                .filter(node -> "hello".equals(node.path("type").asText()))
                .toList();
        assertThat(hellos).hasSize(2);
        assertThat(hellos.get(0).path("session_id").asText()).isEqualTo(device.sessionId());
        assertThat(hellos.get(1).path("session_id").asText()).isEqualTo(device.sessionId());
        // 会话对象本身没被重建
        assertThat(device.session()).isSameAs(sessionBeforeSecondHello);
        // 重复 hello 不是打断，不该产生 tts stop
        assertThat(device.transport().jsonSignatures()).doesNotContain("tts:stop");
        assertThat(device.session().getPlayer().hasContent()).isTrue();
        // 真帧序号从 0 起连续，不跳号不重复（句间静音帧可能穿插，不参与比对）
        List<Integer> indexes = replyFrameIndexes(device);
        assertThat(indexes).isNotEmpty();
        assertThat(indexes).containsExactlyElementsOf(
                IntStream.range(0, indexes.size()).boxed().toList());
    }

    @Test
    void helloWithAecFalseTearsDownServerAecMidCall() {
        FakeDevice device = harness.connect(DEVICE_ID);

        // 首个 hello 声明设备端 AEC 关闭，由服务端消回声
        device.hello(1, false, true);
        device.transport().awaitJson("hello");
        assertThat(harness.aec().serverAecRequiredFlags()).containsExactly(true);
        assertThat(harness.aec().isActive(device.sessionId())).isTrue();

        startPlayback(device, 20);
        AwaitHelper.until("下行音频已开始", () -> !harness.aec().references().isEmpty());
        int referencesAtSecondHello = harness.aec().references().size();

        // 播放途中重复 hello，这次不带 features
        device.sendText(HELLO_WITHOUT_FEATURES);
        AwaitHelper.until("第二条 hello 已回", () -> helloCount(device) == 2);

        // 现存语义：每条 hello 都会重新落一次 features.aec，缺省即拆掉服务端 AEC
        assertThat(harness.aec().serverAecRequiredFlags()).containsExactly(true, false);
        assertThat(harness.aec().isActive(device.sessionId())).isFalse();
        // 拆 AEC 只影响回声消除，不打断播放
        AwaitHelper.until("拆掉服务端 AEC 后仍在下发音频",
                () -> harness.aec().references().size() > referencesAtSecondHello);
        assertThat(device.transport().jsonSignatures()).doesNotContain("tts:stop");
    }

    @Test
    void emptyDeviceIdClosesConnectionWithBadData() {
        // 握手头没有 device-id，URI 也没有任何 query 参数
        FakeWebSocketTransport transport = harness.newTransport();

        FakeDevice device = harness.connect("", transport);

        assertThat(transport.isOpen()).isFalse();
        assertThat(transport.closeStatus()).isNotNull();
        assertThat(transport.closeStatus().getCode()).isEqualTo(CloseStatus.BAD_DATA.getCode());
        // 无主连接不能留下任何会话或设备痕迹
        assertThat(harness.session(device.sessionId())).isNull();
        assertThat(harness.sessionManager().getAllSessions()).isEmpty();
        assertThat(transport.outbound()).isEmpty();
        verify(harness.deviceService(), never()).getBO(anyString());
    }

    /**
     * 设备标识只认 device-id 这一个键，握手鉴权与连接建立两层的认定必须一致。
     * mac_address 是历史遗留写法，官方固件按 device-id 上报，这里不接受，
     * 避免出现「鉴权放行了但连接建立又判空关掉」的不一致。
     */
    @Test
    void macAddressQueryParamIsNotAcceptedAsDeviceId() {
        harness.withBoundDevice(DEVICE_ID, 1);
        FakeWebSocketTransport transport = harness.newTransport()
                .withUri("ws://example.com/ws/xiaozhi/v1/?mac_address=" + DEVICE_ID + "&token=t");

        FakeDevice device = harness.connect(DEVICE_ID, transport);

        assertThat(transport.closeStatus()).isNotNull();
        assertThat(transport.closeStatus().getCode()).isEqualTo(CloseStatus.BAD_DATA.getCode());
        assertThat(harness.sessionManager().getSessionByDeviceId(DEVICE_ID)).isNull();
        assertThat(harness.session(device.sessionId())).isNull();
        verify(harness.deviceService(), never()).getBO(DEVICE_ID);
    }

    /**
     * 浏览器无法设握手头，device-id 走 query 必须能建立连接，
     * 与上一条对照说明被拒的原因是键名而不是 query 解析本身。
     */
    @Test
    void deviceIdKeyInQueryParamRegistersSessionWithoutHeader() {
        harness.withBoundDevice(DEVICE_ID, 1);
        FakeWebSocketTransport transport = harness.newTransport()
                .withUri("ws://example.com/ws/xiaozhi/v1/?device-id=" + DEVICE_ID + "&token=t");

        FakeDevice device = harness.connect(DEVICE_ID, transport);

        assertThat(transport.closeStatus()).isNull();
        ChatSession session = harness.sessionManager().getSessionByDeviceId(DEVICE_ID);
        assertThat(session).isNotNull();
        assertThat(session.getSessionId()).isEqualTo(device.sessionId());
        verify(harness.deviceService()).getBO(DEVICE_ID);
    }

    @Test
    void listenBeforeHelloIsAcceptedAndStartsListening() {
        FakeDevice device = harness.connect(DEVICE_ID);
        // 连接建立时已经初始化过一次 AEC，这里只比对 listen 带来的增量
        int aecInitsBeforeListen = harness.aec().initCalls().size();

        // 不发 hello，直接开始聆听
        device.listenStart(ListenMode.Auto);

        ChatSession session = device.session();
        assertThat(session.getDeviceState()).isEqualTo(DeviceState.LISTENING);
        assertThat(session.getMode()).isEqualTo(ListenMode.Auto);
        // auto 模式由服务端自动断句
        assertThat(harness.vad().autoSegmentOf(device.sessionId())).isTrue();
        assertThat(harness.aec().initCalls()).hasSize(aecInitsBeforeListen + 1);
        assertThat(harness.aec().initCalls()).containsOnly(device.sessionId());
        // 没有 hello 就没有 hello 响应，链路照样往下走
        assertThat(device.transport().textMessages()).isEmpty();
        // 未协商版本时按 v1 裸帧
        assertThat(session.getProtocolVersion()).isEqualTo(BinaryProtocolCodec.VERSION_V1);
    }

    @Test
    void ghostSessionOnAnotherInstanceIsBroadcastClosed() {
        harness.withBoundDevice(DEVICE_ID, 1);
        harness.withBoundDevice(OTHER_DEVICE_ID, 1);
        when(harness.deviceRegistry().getInstance(DEVICE_ID)).thenReturn("other-instance");
        when(harness.deviceRegistry().getInstance(OTHER_DEVICE_ID)).thenReturn(localInstanceId());

        FakeDevice ghost = harness.connect(DEVICE_ID);
        FakeDevice local = harness.connect(OTHER_DEVICE_ID);

        verify(harness.redisBroadcast()).closeDeviceSession(DEVICE_ID);
        // 设备上一次就绑在本实例时不广播，否则会把自己刚建的会话关掉
        verify(harness.redisBroadcast(), never()).closeDeviceSession(OTHER_DEVICE_ID);

        // 广播只针对旧实例，本实例这两条连接照常握手收发
        ghost.hello();
        local.hello();
        assertThat(ghost.transport().awaitJson("hello").path("session_id").asText())
                .isEqualTo(ghost.sessionId());
        assertThat(local.transport().awaitJson("hello").path("session_id").asText())
                .isEqualTo(local.sessionId());
        assertThat(harness.sessionManager().getSessionByDeviceId(DEVICE_ID).getSessionId())
                .isEqualTo(ghost.sessionId());
    }

    /**
     * hello 里的 audio_params 是下划线命名，必须落到会话上，
     * 绑不上的话 MessageHandler#applyAudioParams 永远走 deviceParams == null 的早返回，
     * 参数不一致告警成为死代码，设备声明形同虚设。
     */
    @Test
    void helloAudioParamsAreRecordedOnSession() {
        FakeDevice device = harness.connect(DEVICE_ID);

        device.sendText(HELLO_V2_WITH_FOREIGN_AUDIO_PARAMS);
        device.transport().awaitJson("hello");

        AudioParams recorded = device.session().getDeviceAudioParams();
        assertThat(recorded).isNotNull();
        assertThat(recorded.getFormat()).isEqualTo("opus");
        assertThat(recorded.getSampleRate()).isEqualTo(24000);
        assertThat(recorded.getChannels()).isEqualTo(2);
        assertThat(recorded.getFrameDuration()).isEqualTo(20);
        // 设备声明与服务端处理格式不一致时要能报出差异
        assertThat(recorded.mismatchAgainstServer()).isNotNull();
        // 同一条 hello 里的单词字段绑定不受命名策略影响
        assertThat(device.session().getProtocolVersion()).isEqualTo(2);
        // 服务端能力回显仍是服务端自己的参数
        assertThat(device.transport().awaitJson("hello").path("audio_params").path("sample_rate").asInt())
                .isEqualTo(AudioParams.SERVER_SAMPLE_RATE);
    }

    // 设备声明与服务端一致时不该报差异
    @Test
    void helloWithServerMatchingAudioParamsReportsNoMismatch() {
        FakeDevice device = harness.connect(DEVICE_ID);

        device.sendText(HELLO_WITHOUT_FEATURES);
        device.transport().awaitJson("hello");

        AudioParams recorded = device.session().getDeviceAudioParams();
        assertThat(recorded).isNotNull();
        assertThat(recorded.getSampleRate()).isEqualTo(AudioParams.SERVER_SAMPLE_RATE);
        assertThat(recorded.mismatchAgainstServer()).isNull();
    }

    // ========== 驱动与断言辅助 ==========

    /**
     * 起一轮播放：预编码 Opus 帧直接入队，尾部接一个不结束的流，
     * 让播放在整条用例期间保持进行中（收尾由 harness.shutdown 的 player.stop 负责）。
     */
    private static void startPlayback(FakeDevice device, int frameCount) {
        List<Speech> speeches = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            speeches.add(Speech.ofOpus(taggedFrame(i)));
        }
        device.session().getPlayer()
                .play(Flux.fromIterable(speeches).concatWith(Flux.never()), true);
    }

    /** 构造一帧带序号标记的假 Opus 帧，首字节标记来源、次字节是序号 */
    private static byte[] taggedFrame(int index) {
        byte[] frame = new byte[REPLY_FRAME_LENGTH];
        frame[0] = REPLY_FRAME_MARKER;
        frame[1] = (byte) index;
        return frame;
    }

    /** 下行真帧的序号序列，按发送顺序；播放器补的静音帧不计入 */
    private static List<Integer> replyFrameIndexes(FakeDevice device) {
        List<Integer> indexes = new ArrayList<>();
        for (FakeWebSocketTransport.Sent sent : device.transport().snapshot()) {
            if (!sent.binary()) {
                continue;
            }
            // v1 是裸帧，出站字节即 Opus 负载
            byte[] payload = sent.payload();
            if (payload.length == REPLY_FRAME_LENGTH && payload[0] == REPLY_FRAME_MARKER) {
                indexes.add(payload[1] & 0xFF);
            }
        }
        return indexes;
    }

    private static long helloCount(FakeDevice device) {
        return device.transport().jsonSignatures().stream().filter("hello"::equals).count();
    }

    /** 取装配器里那个 InstanceIdHolder 的实例标识，用于构造「上次也在本实例」的对照 */
    private String localInstanceId() {
        InstanceIdHolder holder = (InstanceIdHolder) ReflectionTestUtils
                .getField(harness.messageHandler(), "instanceIdHolder");
        return holder.getInstanceId();
    }
}

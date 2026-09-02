package com.xiaozhi.communication.protocol;

import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.server.websocket.BinaryProtocolCodec;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.utils.OpusProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住设备二进制帧协议的两组不变量。
 *
 * <p>其一是「收发同源」：会话协商出的版本同时决定上行解帧与下行编帧。v2 才承载 timestamp，
 * 它一路透传到 AEC 对齐入口；v3 头里没有 timestamp，服务端拿不到回显时刻，无法做时间戳对齐；
 * 上行帧与声明版本不符触发降级时，下行编码必须一并降级，否则设备会收到自己解不开的帧。
 * 会话已关闭且 VAD 已重置后到达的音频必须整体丢弃，不得重新拉起链路。
 *
 * <p>其二是「AEC 参考帧的调度契约」：开播后每个节拍下发的帧——真音频帧与补位静音帧一视同仁——
 * 都要按下发顺序、带同一个 timestamp 喂给 AEC；打断时清掉待喂入的参考，新一轮不残留旧轮参考帧。
 *
 * <p>只测调度契约，不测对齐算法本身（延迟估计、ERLE、滤波器收敛由 ReferenceFeedTest 覆盖）；
 * 播放线程时序天生带抖动，因此只断言「有/无」「顺序」「不重不漏」，不断言精确帧数与精确时刻。
 */
class BinaryFrameProtocolTest {

    private static final String DEVICE_ID = "94:a9:90:2b:dd:18";

    /** 高位为 1 的时间戳，用来验证 v2 的 4 字节 timestamp 做的是无符号扩展 */
    private static final long HIGH_BIT_TIMESTAMP = 0x9ABCDEF0L;

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
    void v2UplinkTimestampReachesAecAlignment() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello(2);
        // hello 应答里回的就是协商结果，会话本身也按 v2 解帧
        assertThat(device.transport().awaitJson("hello").path("version").asInt()).isEqualTo(2);
        assertThat(device.session().getProtocolVersion()).isEqualTo(BinaryProtocolCodec.VERSION_V2);

        device.listenStart(ListenMode.Auto);
        byte[] payload = FakeDevice.frame(ScriptedVadService.NO_SPEECH);
        device.sendAudio(payload, HIGH_BIT_TIMESTAMP);

        // 帧头里的 timestamp 原样到达 VAD/AEC 对齐入口，且按无符号扩展，不能变成负数
        assertThat(harness.vad().lastEchoTimestamp()).isEqualTo(HIGH_BIT_TIMESTAMP);
        assertThat(harness.vad().lastEchoTimestamp()).isPositive();
        assertThat(harness.vad().echoTimestamps()).containsExactly(HIGH_BIT_TIMESTAMP);
        // 负载本身不能被帧头污染
        assertThat(harness.vad().processedFrames()).hasSize(1);
        assertThat(hex(harness.vad().processedFrames().get(0))).isEqualTo(hex(payload));
    }

    @Test
    void downgradeToV1AlsoDowngradesDownlinkEncoding() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello(2);
        device.listenStart(ListenMode.Auto);
        assertThat(device.session().getProtocolVersion()).isEqualTo(BinaryProtocolCodec.VERSION_V2);

        // 设备声明了 v2 却发裸 opus：帧长不足 v2 帧头，解帧失败触发降级
        byte[] bareOpus = new byte[10];
        bareOpus[0] = ScriptedVadService.NO_SPEECH;
        bareOpus[9] = 0x7F;
        device.sendRawAudio(bareOpus);

        assertThat(device.session().getProtocolVersion()).isEqualTo(BinaryProtocolCodec.VERSION_V1);
        // 降级后整帧按裸 opus 兜底交给上层，不丢帧也不截断
        assertThat(harness.vad().processedFrames()).hasSize(1);
        assertThat(hex(harness.vad().processedFrames().get(0))).isEqualTo(hex(bareOpus));
        assertThat(harness.vad().lastEchoTimestamp()).isZero();

        // 下行必须跟着降级：v1 是裸帧，长度与 opus 原始长度相等，不带 16 字节头
        byte[] opus = opusPayload((byte) 0x5A, 40);
        byte[] downlink = playSingleFrame(device, opus);
        assertThat(downlink).hasSize(opus.length);
        assertThat(hex(downlink)).isEqualTo(hex(opus));
    }

    @Test
    void v3DeviceGetsZeroTimestampAndNoAecAlignment() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello(3);
        assertThat(device.transport().awaitJson("hello").path("version").asInt()).isEqualTo(3);
        assertThat(device.session().getProtocolVersion()).isEqualTo(BinaryProtocolCodec.VERSION_V3);

        device.listenStart(ListenMode.Auto);
        byte[] payload = FakeDevice.frame(ScriptedVadService.NO_SPEECH);
        // v3 帧头没有 timestamp 字段，设备即使想回显也带不上来
        device.sendAudio(payload, HIGH_BIT_TIMESTAMP);

        assertThat(harness.vad().processedFrames()).hasSize(1);
        assertThat(hex(harness.vad().processedFrames().get(0))).isEqualTo(hex(payload));
        assertThat(harness.vad().lastEchoTimestamp()).isZero();

        // 下行同样是 4 字节头：[type][reserved][payloadSize:2]，没有位置放时间戳
        byte[] opus = opusPayload((byte) 0x3C, 48);
        byte[] downlink = playSingleFrame(device, opus);
        assertThat(downlink).hasSize(opus.length + 4);
        assertThat(downlink[0]).isZero();
        assertThat(downlink[1]).isZero();
        assertThat(((downlink[2] & 0xFF) << 8) | (downlink[3] & 0xFF)).isEqualTo(opus.length);
        BinaryProtocolCodec.Frame decoded = BinaryProtocolCodec.decode(BinaryProtocolCodec.VERSION_V3, downlink);
        assertThat(decoded.timestamp()).isZero();
        assertThat(hex(decoded.payload())).isEqualTo(hex(opus));
    }

    @Test
    void audioAfterSessionClosedIsDropped() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        device.listenStart(ListenMode.Auto);

        // 基线：VAD 已初始化时，上行帧进 VAD
        device.sendFrames(ScriptedVadService.NO_SPEECH);
        assertThat(harness.vad().processedFrames()).hasSize(1);

        // auto 模式下的 listen/stop 取消本次聆听，VAD 会话被重置
        device.listenStop();
        assertThat(harness.vad().autoSegmentOf(device.sessionId())).isNull();

        // 对照组：连接还开着时，VAD 已重置的帧仍会落进唤醒词前置缓冲，说明链路本身是通的
        device.sendFrames(ScriptedVadService.NO_SPEECH);
        ChatSession session = device.session();
        assertThat(session.drainWakeWordAudio()).hasSize(1);

        int vadFramesBefore = harness.vad().processedFrames().size();
        int aecInitBefore = harness.aec().initCalls().size();
        int outboundBefore = device.transport().outbound().size();

        // 连接断了但会话还挂在注册表上：这正是「会话已关闭 + VAD 已重置」的丢弃分支
        device.transport().breakConnection();
        device.sendFrames(ScriptedVadService.NO_SPEECH, ScriptedVadService.NO_SPEECH);

        // 上行帧在调用线程上同步走完整条链路，sendFrames 返回即代表处理结束，无需再等
        assertThat(harness.vad().processedFrames()).hasSize(vadFramesBefore);
        // 一帧都没进唤醒词缓冲，反证 processAudioData 根本没被调到
        assertThat(session.drainWakeWordAudio()).isEmpty();
        // 不因为收到音频而重新拉起 AEC/VAD 会话
        assertThat(harness.aec().initCalls()).hasSize(aecInitBefore);
        assertThat(harness.vad().autoSegmentOf(device.sessionId())).isNull();
        // 丢弃走静默路径：既不回消息，也没有试图往断掉的连接上写
        assertThat(device.transport().outbound()).hasSize(outboundBefore);
        assertThat(device.transport().droppedAfterClose()).isZero();
        assertThat(device.session()).isNotNull();
    }

    @Test
    void everyDownlinkFrameIncludingSilenceFeedsReferenceInOrder() {
        FakeDevice device = harness.connect(DEVICE_ID);
        // v2 会话的下行帧头带 timestamp，才能逐帧比对参考帧的 timestamp
        device.hello(2);
        Player player = device.session().getPlayer();

        byte[][] head = {opusPayload((byte) 0x11, 32), opusPayload((byte) 0x22, 32), opusPayload((byte) 0x33, 32)};
        byte[][] tail = {opusPayload((byte) 0x44, 32)};

        // 上游断流 300ms：这段时间里播放器按节拍补静音帧，设备播放队列整轮不排空
        player.play(Flux.concat(opusFlux(head),
                Mono.delay(Duration.ofMillis(300)).thenMany(opusFlux(tail))), true);
        // tts stop 由发送线程在退出前发出，收到它即代表本轮下发已经停了
        device.transport().awaitJson("tts:stop");

        List<byte[]> downlink = device.transport().binaryPayloads(BinaryProtocolCodec.VERSION_V2);
        List<Long> downlinkTimestamps = device.transport().binaryTimestamps(BinaryProtocolCodec.VERSION_V2);
        List<RecordingAecService.Reference> references = harness.aec().references();

        // 真帧与静音帧一视同仁，下发一帧就喂一帧参考，不重不漏
        assertThat(references).hasSameSizeAs(downlink);
        assertThat(references).allSatisfy(r -> assertThat(r.sessionId()).isEqualTo(device.sessionId()));
        assertThat(hexAll(references.stream().map(RecordingAecService.Reference::frame).toList()))
                .containsExactlyElementsOf(hexAll(downlink));
        // 参考帧的 timestamp 必须与设备实际收到的帧头 timestamp 是同一个值
        assertThat(harness.aec().referenceTimestamps()).containsExactlyElementsOf(downlinkTimestamps);
        assertThat(downlinkTimestamps).isSorted();
        // 真音频帧按原顺序出现，静音帧穿插其间
        assertThat(hexAll(downlink)).containsSubsequence(
                hex(head[0]), hex(head[1]), hex(head[2]), hex(tail[0]));
        assertThat(containsSilence(device.transport(), BinaryProtocolCodec.VERSION_V2)).isTrue();
    }

    @Test
    void abortClearsReferenceAndNextTurnStartsClean() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello(2);
        Player player = device.session().getPlayer();

        byte[][] firstTurn = {opusPayload((byte) 0x61, 32), opusPayload((byte) 0x62, 32), opusPayload((byte) 0x63, 32)};
        // 上游永不结束，播放停在补静音的状态里，等着被打断
        player.play(Flux.concat(opusFlux(firstTurn), Mono.<Speech>never()), true);
        AwaitHelper.until("首轮参考帧已喂入", () -> harness.aec().references().size() >= firstTurn.length);

        // 记下 tts stop 出站那一刻已经发生的 clearReference 次数，用来定先后
        AtomicInteger clearedWhenStopSent = new AtomicInteger(-1);
        device.transport().onTextSent(text -> {
            if (text.contains("\"type\":\"tts\"") && text.contains("\"state\":\"stop\"")) {
                clearedWhenStopSent.compareAndSet(-1, harness.aec().clearReferenceCalls().size());
            }
        });

        // 帧头 timestamp 是 System.currentTimeMillis() 截低 32 位（Player.java:250），基准也要同样截断
        long abortAtMillis = System.currentTimeMillis() & 0xFFFFFFFFL;
        device.abort("wake_word_detected");
        device.transport().awaitJson("tts:stop");

        // 已发送未播放的帧会被设备丢弃，待喂入的参考必须在通知设备停播之前清掉
        assertThat(harness.aec().clearReferenceCalls()).containsExactly(device.sessionId());
        assertThat(clearedWhenStopSent).hasValue(1);

        int settled = awaitReferencesSettled(harness.aec());

        // 新一轮播放：参考帧从零开始，不能带出旧轮残帧
        byte[][] secondTurn = {opusPayload((byte) 0x71, 32), opusPayload((byte) 0x72, 32)};
        player.play(opusFlux(secondTurn), true);
        // 等新一轮自然播完（第二条 tts stop），此时参考帧也已全部喂完
        AwaitHelper.until("新一轮播放已结束", () -> countSignature(device.transport(), "tts:stop") >= 2);

        List<RecordingAecService.Reference> nextTurn =
                harness.aec().references().subList(settled, harness.aec().references().size());
        assertThat(nextTurn).isNotEmpty();
        // 新一轮的参考帧都产生在打断之后
        assertThat(nextTurn.get(0).timestamp()).isGreaterThan(abortAtMillis);
        List<String> nextTurnFrames = hexAll(nextTurn.stream().map(RecordingAecService.Reference::frame).toList());
        assertThat(nextTurnFrames).doesNotContainAnyElementsOf(hexAll(List.of(firstTurn)));
        assertThat(nextTurnFrames).containsSubsequence(hex(secondTurn[0]), hex(secondTurn[1]));
        // 打断只清一次，新一轮不再触发清理
        assertThat(harness.aec().clearReferenceCalls()).hasSize(1);
    }

    // ========== 私有工具 ==========

    /** 让播放器下发一帧预编码 opus，返回传输层收到的第一帧原始字节（含帧头） */
    private static byte[] playSingleFrame(FakeDevice device, byte[] opusFrame) {
        device.session().getPlayer().play(Flux.just(Speech.ofOpus(opusFrame)), false);
        AwaitHelper.until("下行音频帧已发出", () -> !device.transport().binaryFrames().isEmpty());
        return device.transport().binaryFrames().get(0);
    }

    private static long countSignature(FakeWebSocketTransport transport, String signature) {
        return transport.jsonSignatures().stream().filter(signature::equals).count();
    }

    /** 把若干预编码 opus 帧包成一条播放流 */
    private static Flux<Speech> opusFlux(byte[]... frames) {
        return Flux.fromIterable(Arrays.stream(frames).map(Speech::ofOpus).toList());
    }

    /** 下行帧里是否出现过补位静音帧 */
    private static boolean containsSilence(FakeWebSocketTransport transport, int protocolVersion) {
        String silence = hex(OpusProcessor.silenceFrame());
        return hexAll(transport.binaryPayloads(protocolVersion)).contains(silence);
    }

    /**
     * 等到参考帧不再增长，返回稳定后的总数。
     * 打断会中断发送线程，被中断前可能还有一帧在途，直接取快照会把它算进下一轮。
     */
    private static int awaitReferencesSettled(RecordingAecService aec) {
        long quietNs = Duration.ofMillis(150).toNanos();
        AtomicInteger last = new AtomicInteger(-1);
        AtomicLong since = new AtomicLong(System.nanoTime());
        AwaitHelper.until("旧轮参考帧下发已停止", Duration.ofSeconds(5), () -> {
            int count = aec.references().size();
            if (count != last.get()) {
                last.set(count);
                since.set(System.nanoTime());
                return false;
            }
            return System.nanoTime() - since.get() >= quietNs;
        });
        return aec.references().size();
    }

    /** 构造一个内容可辨识的假 opus 帧：首字节是标记，其余按标记递推，避免与静音帧撞车 */
    private static byte[] opusPayload(byte marker, int length) {
        byte[] payload = new byte[length];
        payload[0] = marker;
        for (int i = 1; i < length; i++) {
            payload[i] = (byte) (marker + i);
        }
        return payload;
    }

    /** byte[] 在集合断言里比的是引用，统一转成十六进制串再比内容 */
    private static String hex(byte[] data) {
        return HexFormat.of().formatHex(data);
    }

    private static List<String> hexAll(List<byte[]> data) {
        return data.stream().map(BinaryFrameProtocolTest::hex).toList();
    }
}

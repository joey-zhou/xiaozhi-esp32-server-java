package com.xiaozhi.communication.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.event.ChatAbortedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 钉住「设备主动告别」与「同设备重连」两条链路上的会话生命周期不变量：
 * goodbye 必须一次性拆掉 VAD/AEC 会话、会话注册表、设备反向索引与设备-实例绑定并关闭连接，
 * 之后同一条连接上的业务报文不再产生任何副作用；重连必须拿到全新会话且旧会话可用性不残留，
 * 旧连接迟到的异步收尾不能把已经重连成功的新会话判成离线、也不能拆掉它的绑定；
 * 告别流程清空 player 之后，会话若仍留在注册表里就必须还能被唤醒。
 */
class GoodbyeAndReconnectProtocolTest {

    private static final String DEVICE_ID = "94:a9:90:2b:dd:18";
    private static final String OTHER_DEVICE_ID = "94:a9:90:2b:dd:19";

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
    void goodbyeClosesSessionAndSubsequentWakeIsIgnoredOnWebSocket() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        device.listenStart(ListenMode.Auto);
        String sessionId = device.sessionId();
        // 先确认 goodbye 之前 VAD/AEC 确实是活的，否则后面的「已清理」断言等于没测
        assertThat(harness.vad().autoSegmentOf(sessionId)).isTrue();
        assertThat(harness.aec().initCalls()).contains(sessionId);

        device.goodbye();

        // VAD 会话被拆掉：autoSegment 记录消失即 resetSession 生效
        assertThat(harness.vad().autoSegmentOf(sessionId)).isNull();
        // AEC 会话同样被拆掉，整条 goodbye 链路上只重置一次
        assertThat(harness.aec().resetCalls()).containsExactly(sessionId);
        // 中止事件带的原因是设备主动退出，区别于普通打断
        List<ChatAbortedEvent> aborts = harness.events().eventsOf(ChatAbortedEvent.class);
        assertThat(aborts).hasSize(1);
        assertThat(aborts.get(0).getReason()).isEqualTo("设备主动退出");
        assertThat(aborts.get(0).getDeviceId()).isEqualTo(DEVICE_ID);
        // 会话注册表与设备反向索引一并清空
        assertThat(harness.sessionManager().getSession(sessionId)).isNull();
        assertThat(harness.sessionManager().getSessionByDeviceId(DEVICE_ID)).isNull();
        // 设备-实例绑定解除，连接由服务端关闭
        verify(harness.deviceRegistry()).unbind(DEVICE_ID);
        assertThat(device.transport().isOpen()).isFalse();
        assertThat(device.transport().closeStatus()).isNotNull();
        // 关连接前先下发 tts stop 通知设备停播，此外不再有别的出站
        assertThat(device.transport().jsonSignatures()).containsExactly("hello", "tts:stop");

        // 反向断言前先等 goodbye 的后置信号落定，再发唤醒词
        AwaitHelper.until("goodbye 收尾完成：会话已注销且 tts stop 已出站",
                () -> harness.sessionManager().getSession(sessionId) == null
                        && device.transport().jsonSignatures().contains("tts:stop"));
        int outboundAfterGoodbye = device.transport().outbound().size();

        device.listenDetect("你好小智");

        // 已关闭的连接上唤醒词不该重新拉起会话、VAD 或任何出站
        AwaitHelper.stayFalse("goodbye 后的 listen/detect 又产生了副作用", Duration.ofMillis(200),
                () -> device.transport().outbound().size() > outboundAfterGoodbye
                        || harness.vad().autoSegmentOf(sessionId) != null
                        || harness.sessionManager().getSession(sessionId) != null);
        verify(harness.personaFactory(), never()).buildPersona(any(ChatSession.class));
    }

    /**
     * 设备主动 goodbye 之后库里必须写入一条离线。goodbye 走 closeSession 已把会话摘出注册表，
     * 容器随后回调 afterConnectionClosed 时取不到会话，写库只能由 closeSession 自己负责，
     * 漏写会让设备管理页长期显示假在线，只能等下次服务启动的批量重置纠正。
     */
    @Test
    void goodbyeThenTransportCloseMarksDeviceOffline() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        AwaitHelper.until("连接建立时的上线写库已落地",
                () -> countState(DEVICE_ID, DeviceBO.DEVICE_STATE_ONLINE) == 1);

        device.goodbye();
        // goodbye 里 closeSession 会真的关连接，容器随后回调 afterConnectionClosed
        device.disconnect();

        AwaitHelper.until("离线写库已落地",
                () -> countState(DEVICE_ID, DeviceBO.DEVICE_STATE_OFFLINE) == 1);
        // 连接真正关闭时的回调取不到会话，不能再重复写一条
        AwaitHelper.stayFalse("离线状态被重复写入", Duration.ofMillis(200),
                () -> countState(DEVICE_ID, DeviceBO.DEVICE_STATE_OFFLINE) > 1);
        assertThat(countState(DEVICE_ID, DeviceBO.DEVICE_STATE_ONLINE)).isEqualTo(1);
    }

    @Test
    void reconnectAfterGoodbyeGetsFreshSessionAndWorks() {
        FakeDevice first = harness.connect(DEVICE_ID);
        first.hello();
        first.goodbye();
        String oldSessionId = first.sessionId();
        AwaitHelper.until("旧会话已注销",
                () -> harness.sessionManager().getSession(oldSessionId) == null);

        // 换一条新连接（新 sessionId）重连同一台设备
        FakeDevice second = harness.connect(DEVICE_ID);
        second.hello();

        JsonNode hello = second.transport().awaitJson("hello");
        assertThat(second.sessionId()).isNotEqualTo(oldSessionId);
        assertThat(hello.path("session_id").asText()).isEqualTo(second.sessionId());
        ChatSession fresh = harness.sessionManager().getSession(second.sessionId());
        assertThat(fresh).isNotNull();
        // 设备反向索引指向新会话，旧 sessionId 不再可解析
        assertThat(harness.sessionManager().getSessionByDeviceId(DEVICE_ID)).isSameAs(fresh);
        assertThat(harness.sessionManager().getSession(oldSessionId)).isNull();

        // 新会话上唤醒词照常触发唤醒流程
        second.listenDetect("你好小智");
        verify(harness.personaFactory()).buildPersona(fresh);
        assertThat(fresh.getDeviceState()).isEqualTo(DeviceState.SPEAKING);

        // 新会话上的上行音频链路同样是通的
        second.listenStart(ListenMode.Auto);
        assertThat(harness.vad().autoSegmentOf(second.sessionId())).isTrue();
        second.speak(ScriptedVadService.SPEECH_START,
                ScriptedVadService.SPEECH_CONTINUE,
                ScriptedVadService.SPEECH_END);
        AwaitHelper.until("新会话本轮识别已收到音频并结束",
                () -> harness.stt().completedStreams() == 1);
        assertThat(harness.stt().receivedFrames()).isNotEmpty();
    }

    @Test
    void reconnectRaceDoesNotMarkNewSessionOffline() {
        FakeDevice stale = harness.connect(DEVICE_ID);
        stale.hello();
        String staleSessionId = stale.sessionId();

        // 设备先在新连接上重连成功，旧连接的关闭回调随后才到
        FakeDevice fresh = harness.connect(DEVICE_ID);
        fresh.hello();
        String freshSessionId = fresh.sessionId();
        assertThat(harness.sessionManager().getSessionByDeviceId(DEVICE_ID))
                .isSameAs(harness.sessionManager().getSession(freshSessionId));

        stale.disconnect();

        // 旧会话被注销，但设备反向索引仍指向新会话（按 sessionId 匹配才删）
        assertThat(harness.sessionManager().getSession(staleSessionId)).isNull();
        assertThat(harness.sessionManager().getSession(freshSessionId)).isNotNull();
        assertThat(harness.sessionManager().getSessionByDeviceId(DEVICE_ID))
                .isSameAs(harness.sessionManager().getSession(freshSessionId));

        // 反向断言的 settle：先等两次连接的上线写库落地，再关一台无关设备并等它的离线写库落地，
        // 旧会话那条更早启动的写库虚拟线程此时必已跑完
        AwaitHelper.until("新旧两次连接的上线写库都已落地",
                () -> countState(DEVICE_ID, DeviceBO.DEVICE_STATE_ONLINE) == 2);
        FakeDevice other = harness.connect(OTHER_DEVICE_ID);
        other.hello();
        other.disconnect();
        AwaitHelper.until("无关设备的离线写库已落地",
                () -> countState(OTHER_DEVICE_ID, DeviceBO.DEVICE_STATE_OFFLINE) == 1);

        // 时序保护生效：设备已在新会话上，旧会话不得把它写成离线
        assertThat(countState(DEVICE_ID, DeviceBO.DEVICE_STATE_OFFLINE)).isZero();
    }

    /**
     * 设备在新连接上重连后，旧连接的收尾不能解除设备-实例绑定。
     * 解错了新会话存续期间 Redis 就查不到这台设备：refreshDeviceRegistry 的 expire 对不存在的 key
     * 是空操作救不回来，getOwnDeviceIds 漏掉它，跨实例幽灵会话清理也定位不到。
     */
    @Test
    void reconnectRaceKeepsDeviceBoundToInstance() {
        FakeDevice stale = harness.connect(DEVICE_ID);
        stale.hello();

        FakeDevice fresh = harness.connect(DEVICE_ID);
        fresh.hello();
        String freshSessionId = fresh.sessionId();

        stale.disconnect();

        AwaitHelper.until("旧会话已注销",
                () -> harness.sessionManager().getSession(stale.sessionId()) == null);
        // 设备自始至终在线，只是换了连接
        assertThat(harness.sessionManager().getSession(freshSessionId)).isNotNull();
        assertThat(harness.sessionManager().getSessionByDeviceId(DEVICE_ID).getSessionId())
                .isEqualTo(freshSessionId);
        verify(harness.deviceRegistry(), never()).unbind(DEVICE_ID);
    }

    /** 没有重连时旧连接关闭要正常解绑，否则实例上会留下查得到却已不存在的设备 */
    @Test
    void closingTheOnlySessionUnbindsDevice() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();

        device.disconnect();

        AwaitHelper.until("会话已注销",
                () -> harness.sessionManager().getSession(device.sessionId()) == null);
        verify(harness.deviceRegistry()).unbind(DEVICE_ID);
    }

    /**
     * 告别流程清空 player 之后会话仍留在注册表里，设备随时可能再发唤醒词。
     * player 为空要视同没有待执行回调，唤醒词照常走 Persona 重建，否则设备再也唤不醒。
     */
    @Test
    void listenAfterInactivityGoodbyeRebuildsPersonaWhenPlayerCleared() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();
        ChatSession session = device.session();
        assertThat(session.getPlayer()).isNotNull();

        clearPlaybackRuntime(session);

        device.listenDetect("你好小智");

        assertThat(harness.sessionManager().getSession(device.sessionId())).isSameAs(session);
        assertThat(device.transport().isOpen()).isTrue();
        verify(harness.personaFactory()).buildPersona(session);
    }

    /** 等价于告别语播放完成后 functionAfterChat 执行完的会话状态 */
    private static void clearPlaybackRuntime(ChatSession session) {
        session.getPlayer().stop();
        session.setPersona(null);
        session.setPlayer(null);
    }

    /** 指定设备被写入指定状态的次数 */
    private int countState(String deviceId, String state) {
        return (int) harness.deviceRepository().stateUpdates().stream()
                .filter(update -> deviceId.equals(update.deviceId()) && state.equals(update.state()))
                .count();
    }
}

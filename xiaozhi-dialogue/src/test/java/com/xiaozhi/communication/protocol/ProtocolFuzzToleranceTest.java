package com.xiaozhi.communication.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.communication.domain.iot.IotDescriptor;
import com.xiaozhi.communication.domain.iot.IotState;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.enums.ListenMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 钉住两条协议不变量：畸形上行不能弄坏会话，未绑定设备不能吃到对话链路。
 *
 * <p>其一，设备侧固件版本参差，未知 type、缺字段、非 JSON、非法枚举值都会真实出现在线上。
 * 服务端的契约是「整条报文丢掉，连接与会话状态原样保留」——不回错误帧、不改设备状态、
 * 不把后续合法报文一起带坏。每条用例都在畸形报文之后再发一条合法报文，用它的可观测结果
 * 证明 handler 没有进入坏状态，而不是只看没抛异常。
 *
 * <p>其二，roleId 为空的设备只能走验证码播报，验证码在一串消息里只能生成一次，
 * 其音频必须整体丢弃而不是进唤醒词前置缓冲；{@code user_chat_} 虚拟设备例外，
 * 它自动建档绑定后当前这条消息要继续被处理，不能被验证码流程截胡。
 *
 * <p>盲区同 {@link ProtocolTestHarness}：VAD/AEC/STT/TTS 均为假体，这里只断言分派与丢弃，
 * 不断言识别质量与精确帧数。
 */
class ProtocolFuzzToleranceTest {

    private static final String BOUND_DEVICE_ID = "94:a9:90:2b:dd:18";
    private static final String UNBOUND_DEVICE_ID = "94:a9:90:2b:dd:19";
    private static final String VIRTUAL_DEVICE_ID = "user_chat_7";
    private static final int VIRTUAL_USER_ID = 7;

    private ProtocolTestHarness harness;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        harness = ProtocolTestHarness.create();
    }

    @AfterEach
    void tearDown() {
        harness.shutdown();
    }

    // ========== 畸形上行的容错 ==========

    // 设备回来的 mcp 消息可能没有 payload 或没有 id，取请求号不能抛异常打断消息处理
    @Test
    void mcpMessageWithoutPayloadIsIgnoredAndConnectionSurvives() {
        FakeDevice device = harness.connect(BOUND_DEVICE_ID);
        device.hello();
        device.transport().awaitJson("hello");
        device.transport().clearOutbound();

        device.sendText("{\"type\":\"mcp\"}");
        device.sendText("{\"type\":\"mcp\",\"payload\":{}}");

        // 后置信号：紧接着的合法 listen/start 必须被正常处理
        device.listenStart(ListenMode.Auto);
        AwaitHelper.until("合法 listen/start 已初始化 VAD",
                () -> Boolean.TRUE.equals(harness.vad().autoSegmentOf(device.sessionId())));

        assertThat(device.transport().isOpen()).isTrue();
        assertThat(device.transport().closeStatus()).isNull();
        assertThat(harness.session(device.sessionId())).isNotNull();
    }

    @Test
    void unknownMessageTypeIsIgnoredAndConnectionSurvives() {
        FakeDevice device = harness.connect(BOUND_DEVICE_ID);
        device.hello();
        device.transport().awaitJson("hello");
        device.transport().clearOutbound();

        // type 不在 @JsonSubTypes 列表里，反序列化返回 null，整条报文被吞
        device.sendText("{\"type\":\"whatever\",\"x\":1}");

        // 后置信号：紧接着的合法 listen/start 必须被正常处理
        device.listenStart(ListenMode.Auto);
        AwaitHelper.until("合法 listen/start 已初始化 VAD",
                () -> Boolean.TRUE.equals(harness.vad().autoSegmentOf(device.sessionId())));

        assertThat(device.transport().isOpen()).isTrue();
        assertThat(device.transport().closeStatus()).isNull();
        // 未知类型既不回错误帧也不回任何应答
        assertThat(device.transport().textMessages()).isEmpty();
        assertThat(device.transport().binaryFrames()).isEmpty();
        assertThat(harness.session(device.sessionId())).isNotNull();
    }

    @Test
    void listenWithoutStateAndMalformedJsonDoNotKillSession() {
        FakeDevice device = harness.connect(BOUND_DEVICE_ID);
        device.hello();
        device.transport().awaitJson("hello");
        device.transport().clearOutbound();

        // state 缺失：enum switch 直接 NPE，必须被 handleTextMessage 兜住
        device.sendText("{\"type\":\"listen\"}");
        // 根本不是 JSON
        device.sendText("not json at all");
        // 空帧
        device.sendText("");
        // 超长且 state 是非法枚举值：反序列化失败，同样整条丢掉
        device.sendText("{\"type\":\"listen\",\"state\":\"bogus\",\"text\":\"" + "啊".repeat(20000) + "\"}");

        // 后置信号：补一条 hello 并等应答，确认四条坏报文都已被处理完
        device.hello();
        AwaitHelper.until("坏报文之后的 hello 已应答",
                () -> device.transport().jsonSignatures().contains("hello"));
        // 四条都没有走到 listen 分支：VAD 未被初始化，也没有任何应答帧
        assertThat(harness.vad().autoSegmentOf(device.sessionId())).isNull();
        assertThat(device.transport().jsonSignatures()).containsOnly("hello");
        assertThat(device.transport().binaryFrames()).isEmpty();
        assertThat(device.transport().isOpen()).isTrue();

        // handler 没进坏状态：紧接着的合法 listen/start 仍被正确处理
        device.listenStart(ListenMode.Manual);
        AwaitHelper.until("合法 listen/start 已初始化 VAD",
                () -> harness.vad().autoSegmentOf(device.sessionId()) != null);
        // manual 由客户端断句，autoSegment 必须为 false，证明走的是真实分支而非兜底
        assertThat(harness.vad().autoSegmentOf(device.sessionId())).isFalse();
    }

    @Test
    void iotDescriptorsAndStatesAreDispatchedSeparately() {
        List<IotDescriptor> dispatchedDescriptors = new CopyOnWriteArrayList<>();
        List<IotState> dispatchedStates = new CopyOnWriteArrayList<>();
        List<String> descriptorSessions = new CopyOnWriteArrayList<>();
        List<String> stateSessions = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            descriptorSessions.add(invocation.getArgument(0));
            dispatchedDescriptors.addAll(invocation.<List<IotDescriptor>>getArgument(1));
            return null;
        }).when(harness.iotService()).handleDeviceDescriptors(anyString(), anyList());
        doAnswer(invocation -> {
            stateSessions.add(invocation.getArgument(0));
            dispatchedStates.addAll(invocation.<List<IotState>>getArgument(1));
            return null;
        }).when(harness.iotService()).handleDeviceStates(anyString(), anyList());

        FakeDevice device = harness.connect(BOUND_DEVICE_ID);
        device.hello();
        device.transport().awaitJson("hello");
        String sessionId = device.sessionId();

        // 一条报文同时带两个字段，两条分支各走一次，参数是各自的子结构
        device.iot("[{\"name\":\"Speaker\",\"description\":\"扬声器\"}]",
                "[{\"name\":\"Speaker\",\"state\":{\"volume\":50}}]");

        AwaitHelper.until("descriptors 已分派", () -> dispatchedDescriptors.size() == 1);
        AwaitHelper.until("states 已分派", () -> dispatchedStates.size() == 1);
        assertThat(descriptorSessions).containsExactly(sessionId);
        assertThat(stateSessions).containsExactly(sessionId);
        assertThat(dispatchedDescriptors.get(0).getName()).isEqualTo("Speaker");
        assertThat(dispatchedStates.get(0).getName()).isEqualTo("Speaker");
        assertThat(dispatchedStates.get(0).getState()).containsEntry("volume", 50);

        // 只带 descriptors：states 分支不能被带着一起触发
        dispatchedDescriptors.clear();
        dispatchedStates.clear();
        device.iot("[{\"name\":\"Lamp\"}]", null);
        AwaitHelper.until("descriptors 已分派", () -> dispatchedDescriptors.size() == 1);
        assertThat(dispatchedDescriptors.get(0).getName()).isEqualTo("Lamp");
        assertThat(dispatchedStates).isEmpty();

        // 只带 states：反过来同理
        dispatchedDescriptors.clear();
        dispatchedStates.clear();
        device.iot(null, "[{\"name\":\"Lamp\",\"state\":{\"power\":\"on\"}}]");
        AwaitHelper.until("states 已分派", () -> dispatchedStates.size() == 1);
        assertThat(dispatchedStates.get(0).getState()).containsEntry("power", "on");
        assertThat(dispatchedDescriptors).isEmpty();
    }

    @Test
    void helloWithMissingTypeFieldGetsNoResponseAndKeepsConnection() {
        FakeDevice device = harness.connect(BOUND_DEVICE_ID);

        // 缺 type 字段，Jackson 认不出子类型，握手报文整条丢掉，服务端不回 hello
        device.sendText("{\"version\":1,\"transport\":\"websocket\","
                + "\"audio_params\":{\"format\":\"opus\",\"sample_rate\":16000,"
                + "\"channels\":1,\"frame_duration\":60}}");

        assertThat(device.transport().isOpen()).isTrue();

        // 后置信号：补一条完整 hello 仍能握手成功，说明连接没被前一条带坏
        device.hello();
        JsonNode hello = device.transport().awaitJson("hello");
        assertThat(hello.path("session_id").asText()).isEqualTo(device.sessionId());
        // 全程只回了这一条 hello，缺 type 的那条没有产生任何应答
        assertThat(device.transport().jsonSignatures()).containsExactly("hello");
    }

    @Test
    void emptyBinaryFramesAreDroppedBeforeVadAndAudioResumes() {
        FakeDevice device = harness.connect(BOUND_DEVICE_ID);
        device.hello();
        device.transport().awaitJson("hello");
        device.listenStart(ListenMode.Auto);

        // 零长二进制帧：解帧后 payload 为空，必须在进 VAD 之前被丢掉
        device.sendRawAudio(new byte[0]);
        device.sendRawAudio(new byte[0]);

        // 后置信号：随后的正常语音仍走完整链路
        device.speak(ScriptedVadService.SPEECH_START, ScriptedVadService.SPEECH_END);
        AwaitHelper.until("本轮识别已结束", () -> harness.stt().completedStreams() == 1);

        List<byte[]> processed = harness.vad().processedFrames();
        assertThat(processed).noneMatch(frame -> frame.length == 0);
        assertThat(processed).hasSize(2);
        assertThat(processed.get(0)[0]).isEqualTo(ScriptedVadService.SPEECH_START);
        assertThat(processed.get(1)[0]).isEqualTo(ScriptedVadService.SPEECH_END);
        assertThat(device.transport().isOpen()).isTrue();
    }

    // ========== 未绑定设备 ==========

    @Test
    void unboundDeviceGetsVerificationCodeOnceForBurstOfMessages() throws IOException {
        harness.withUnboundDevice(UNBOUND_DEVICE_ID);
        stubVerifyCode("246813");

        FakeDevice device = harness.connect(UNBOUND_DEVICE_ID);
        device.hello();
        device.transport().awaitJson("hello");
        device.transport().clearOutbound();

        // 设备端连发三条，验证码流程只能启动一次
        device.listenStart(ListenMode.Auto);
        device.listenStart(ListenMode.Auto);
        device.listenStart(ListenMode.Auto);

        JsonNode sentenceStart = device.transport().awaitJson("tts:sentence_start");
        assertThat(sentenceStart.path("text").asText()).isEqualTo("246813");
        AwaitHelper.until("验证码音频已开始下发",
                () -> !device.transport().binaryFrames().isEmpty());
        AwaitHelper.stayFalse("验证码被重复生成", Duration.ofMillis(200),
                () -> invocationCount(harness.deviceService(), "generateCode") > 1);
        verify(harness.deviceService(), times(1))
                .generateCode(UNBOUND_DEVICE_ID, device.sessionId(), "esp32");

        // 三条 listen 都没有进 handleListenMessage
        assertThat(harness.vad().autoSegmentOf(device.sessionId())).isNull();
    }

    @Test
    void unboundDeviceAudioIsDroppedNotBuffered() {
        harness.withUnboundDevice(UNBOUND_DEVICE_ID);
        FakeDevice device = harness.connect(UNBOUND_DEVICE_ID);
        device.hello();
        device.transport().awaitJson("hello");
        device.transport().clearOutbound();

        // roleId 检查在 VAD 之前，这些帧既不进识别也不进唤醒词缓冲
        device.sendFrames(ScriptedVadService.SPEECH_START,
                ScriptedVadService.SPEECH_CONTINUE, ScriptedVadService.SPEECH_CONTINUE,
                ScriptedVadService.SPEECH_CONTINUE, ScriptedVadService.SPEECH_CONTINUE,
                ScriptedVadService.SPEECH_CONTINUE, ScriptedVadService.SPEECH_CONTINUE,
                ScriptedVadService.SPEECH_CONTINUE, ScriptedVadService.SPEECH_CONTINUE,
                ScriptedVadService.SPEECH_END);

        // 后置信号：再发一条 hello，它的应答出站说明前面十帧已被链路消化完
        device.hello();
        AwaitHelper.until("音频帧之后的 hello 已应答",
                () -> device.transport().jsonSignatures().contains("hello"));

        assertThat(harness.session(device.sessionId()).drainWakeWordAudio()).isEmpty();
        assertThat(harness.vad().processedFrames()).isEmpty();
        assertThat(harness.stt().streamCalls()).isZero();
    }

    @Test
    void userChatVirtualDeviceAutoBindsThenProcessesMessage() {
        harness.withUnboundDevice(VIRTUAL_DEVICE_ID);
        stubVirtualDeviceBinding();

        FakeDevice device = harness.connect(VIRTUAL_DEVICE_ID);
        device.hello();
        device.transport().awaitJson("hello");
        device.transport().clearOutbound();

        device.listenText("今天天气怎么样");

        // 自动建档：web 类型、绑定到默认角色
        AwaitHelper.until("虚拟设备已建档", () -> !harness.deviceRepository().savedDevices().isEmpty());
        assertThat(harness.deviceRepository().savedDevices()).singleElement().satisfies(saved -> {
            assertThat(saved.getDeviceId()).isEqualTo(VIRTUAL_DEVICE_ID);
            assertThat(saved.getType()).isEqualTo("web");
            assertThat(saved.getUserId()).isEqualTo(VIRTUAL_USER_ID);
            assertThat(saved.getRoleId()).isEqualTo(1);
        });

        // 绑定后当前这条 listen/text 继续被处理，而不是像验证码分支那样被丢弃
        JsonNode stt = device.transport().awaitJson("stt");
        assertThat(stt.path("text").asText()).isEqualTo("今天天气怎么样");
        assertThat(harness.session(device.sessionId()).getDevice().getRoleId()).isEqualTo(1);
        verify(harness.deviceService(), never()).generateCode(anyString(), anyString(), anyString());
    }

    @Test
    void malformedMessageFromUnboundDeviceDoesNotTriggerVerificationCode() throws IOException {
        harness.withUnboundDevice(UNBOUND_DEVICE_ID);
        stubVerifyCode("135790");

        FakeDevice device = harness.connect(UNBOUND_DEVICE_ID);
        device.hello();
        device.transport().awaitJson("hello");
        device.transport().clearOutbound();

        // 反序列化失败发生在未绑定分支之前，坏报文不该把设备推进验证码流程
        device.sendText("{\"type\":\"nope\"}");
        device.sendText("{");

        // 后置信号：再发一条 hello 并等应答，确认坏报文已被处理完
        device.hello();
        AwaitHelper.until("坏报文之后的 hello 已应答",
                () -> device.transport().jsonSignatures().contains("hello"));
        verify(harness.deviceService(), never()).generateCode(anyString(), anyString(), anyString());
        assertThat(device.transport().jsonSignatures()).containsOnly("hello");

        // 对照组：同一条连接上发合法消息，验证码流程确实是可达的
        device.listenStart(ListenMode.Auto);
        AwaitHelper.until("验证码已生成",
                () -> invocationCount(harness.deviceService(), "generateCode") == 1);
    }

    // ========== 私有辅助 ==========

    /** 统计 mock 上某个方法名被调用的次数，供 AwaitHelper 轮询异步分派 */
    private static long invocationCount(Object mock, String methodName) {
        return Mockito.mockingDetails(mock).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(methodName))
                .count();
    }

    /** 让 deviceService 返回一份带现成音频的验证码，避开 TTS 合成 */
    private void stubVerifyCode(String code) throws IOException {
        VerifyCodeBO verifyCode = new VerifyCodeBO();
        verifyCode.setDeviceId(UNBOUND_DEVICE_ID);
        verifyCode.setCode(code);
        verifyCode.setAudioPath(silentPcm().toString());
        doAnswer(invocation -> verifyCode).when(harness.deviceService())
                .generateCode(anyString(), anyString(), anyString());
    }

    /** 180ms 静音 PCM，Player 按 60ms 分块后走真实 Opus 编码下发 */
    private Path silentPcm() throws IOException {
        Path path = tempDir.resolve("verify-code.pcm");
        Files.write(path, new byte[3840 * 3]);
        return path;
    }

    /** user_chat_ 虚拟设备：建档前查不到角色，deviceRepository.save 之后才查得到绑定后的设备 */
    private void stubVirtualDeviceBinding() {
        DeviceBO unbound = new DeviceBO();
        unbound.setDeviceId(VIRTUAL_DEVICE_ID);
        unbound.setType("web");

        DeviceBO bound = new DeviceBO();
        bound.setDeviceId(VIRTUAL_DEVICE_ID);
        bound.setDeviceName("小助手");
        bound.setType("web");
        bound.setUserId(VIRTUAL_USER_ID);
        bound.setRoleId(1);

        RoleBO defaultRole = new RoleBO();
        defaultRole.setRoleId(1);
        defaultRole.setUserId(VIRTUAL_USER_ID);
        defaultRole.setRoleName("协议测试角色");

        doAnswer(invocation -> savedVirtualDevice() != null ? bound : unbound)
                .when(harness.deviceService()).getBO(VIRTUAL_DEVICE_ID);
        doAnswer(invocation -> defaultRole)
                .when(harness.roleService()).getDefaultOrFirstBO(VIRTUAL_USER_ID);
    }

    private Device savedVirtualDevice() {
        return harness.deviceRepository().savedDevices().stream()
                .filter(device -> VIRTUAL_DEVICE_ID.equals(device.getDeviceId()))
                .findFirst()
                .orElse(null);
    }
}

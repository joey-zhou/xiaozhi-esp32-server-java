package com.xiaozhi.communication.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.communication.domain.AudioParams;
import com.xiaozhi.enums.ListenMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 脚手架自检：确认「传输层假体 + 真实对象图」这条链路是通的。
 * 这两条用例不覆盖协议语义，只钉住装配本身——装配一旦坏掉（生产类新增注入字段、
 * 事件监听器被摘、音频流生命周期被改），后续所有协议用例都会以难以定位的方式变红，
 * 先在这里失败能直接指出是脚手架问题而不是被测行为问题。
 */
class ProtocolHarnessSmokeTest {

    private static final String DEVICE_ID = "94:a9:90:2b:dd:18";

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
    void helloEchoesSessionIdAndServerAudioParams() {
        FakeDevice device = harness.connect(DEVICE_ID);

        device.hello();

        JsonNode hello = device.transport().awaitJson("hello");
        assertThat(hello.path("session_id").asText()).isEqualTo(device.sessionId());
        assertThat(hello.path("transport").asText()).isEqualTo("websocket");
        assertThat(hello.path("version").asInt()).isEqualTo(1);
        // 下发的是服务端处理链路固定的参数，不是设备声明的参数
        JsonNode audioParams = hello.path("audio_params");
        assertThat(audioParams.path("format").asText()).isEqualTo(AudioParams.SERVER_FORMAT);
        assertThat(audioParams.path("sample_rate").asInt()).isEqualTo(AudioParams.SERVER_SAMPLE_RATE);
        assertThat(audioParams.path("channels").asInt()).isEqualTo(AudioParams.SERVER_CHANNELS);
        assertThat(audioParams.path("frame_duration").asInt()).isEqualTo(AudioParams.SERVER_FRAME_DURATION);
        // 握手阶段不该有任何下行音频
        assertThat(device.transport().binaryFrames()).isEmpty();
    }

    @Test
    void listenStartThenAudioFramesReachSessionAudioStream() {
        FakeDevice device = harness.connect(DEVICE_ID);
        device.hello();

        device.listenStart(ListenMode.Auto);
        device.speak(ScriptedVadService.SPEECH_START,
                ScriptedVadService.SPEECH_CONTINUE,
                ScriptedVadService.SPEECH_CONTINUE,
                ScriptedVadService.SPEECH_END);

        // auto 模式下服务端自动断句，VAD 按 autoSegment=true 初始化
        assertThat(harness.vad().autoSegmentOf(device.sessionId())).isTrue();
        // 四帧全部过了 VAD，SPEECH_END 那帧不进音频流
        assertThat(harness.vad().processedFrames()).hasSize(4);
        AwaitHelper.until("本轮识别已收到全部音频帧并结束",
                () -> harness.stt().completedStreams() == 1);
        List<byte[]> sttFrames = harness.stt().receivedFrames();
        assertThat(sttFrames).hasSize(3);
        assertThat(sttFrames).containsExactlyElementsOf(harness.vad().processedFrames().subList(0, 3));
    }
}

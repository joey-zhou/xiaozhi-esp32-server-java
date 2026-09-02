package com.xiaozhi.dialogue.audio;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.audio.VadService.VadResult;
import com.xiaozhi.dialogue.audio.VadService.VadStatus;
import com.xiaozhi.dialogue.audio.vad.SileroVadModel;
import com.xiaozhi.dialogue.audio.vad.VadModel.InferenceResult;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.OpusProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住断句状态机：这是每一轮对话都要走的核心链路。
 *
 * <p>四条不变量：
 * <ul>
 *   <li>manual 模式不跑 Silero，首帧即 SPEECH_START，其余持续 SPEECH_CONTINUE，收句只由 finishSegment 触发；</li>
 *   <li>SPEECH_END 按 (静音时长 - tailKeepMs) / 静音时长 的比例从 pcmData 尾部删帧，
 *       比例算错会把用户话尾一起删掉造成丢字，删多了要以实际帧数为界不能越界；</li>
 *   <li>连续 30 帧静音必须清零 GRU 隐状态，否则长静音后 VAD 再也拉不起来、设备变哑巴；</li>
 *   <li>角色没配阈值时退回默认值（起播 0.4），退化会让全量设备误触发或不触发。</li>
 * </ul>
 *
 * <p>Silero 模型换成替身，测试不加载 ONNX；喂进去的都是能量远高于门限的方波，
 * 静音与否完全由替身返回的概率决定，与解码后的真实能量无关。
 */
@ExtendWith(MockitoExtension.class)
class VadServiceTest {

    private static final String SESSION_ID = "vad-session";
    private static final Integer ROLE_ID = 7;

    @Mock
    private SileroVadModel vadModel;
    @Mock
    private RoleService roleService;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    private VadService vadService;

    /** 替身每帧返回的语音概率，由各用例改写 */
    private volatile float speechProbability;
    /** 每次推理拿到的上一时刻隐状态首元素，用于观察 GRU 是否被清零 */
    private final List<Float> inboundStateHeads = new ArrayList<>();

    @BeforeEach
    void setUp() {
        vadService = new VadService();
        ReflectionTestUtils.setField(vadService, "vadModel", vadModel);
        ReflectionTestUtils.setField(vadService, "roleService", roleService);
        ReflectionTestUtils.setField(vadService, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(vadService, "preBufferMs", 500);
        ReflectionTestUtils.setField(vadService, "tailKeepMs", 300);
        // aecService 留空：生产代码对其判空，本类只关心断句状态机

        lenient().when(springSession.getId()).thenReturn(SESSION_ID);
        WebSocketSession session = new WebSocketSession(springSession);
        session.setDevice(device());
        lenient().when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        lenient().when(roleService.getBO(ROLE_ID)).thenReturn(role(0.5f, 0.4f, 800));

        lenient().when(vadModel.infer(any(), any(), any())).thenAnswer(invocation -> {
            float[][][] previousState = invocation.getArgument(2);
            inboundStateHeads.add(previousState[0][0][0]);
            // 返回非零隐状态，重置后再次推理拿到的就是 0
            float[][][] nextState = new float[2][1][128];
            nextState[0][0][0] = 1.0f;
            return new InferenceResult(speechProbability, nextState);
        });
    }

    @Test
    void uninitializedSessionReturnsNull() {
        // DialogueService 据此判定是 listen/start 之前补发的唤醒词音频，只采集不送识别
        assertThat(vadService.processAudio(SESSION_ID, loudOpusFrame())).isNull();
    }

    @Test
    void manualModeStartsOnFirstFrameThenContinues() {
        vadService.initSession(SESSION_ID, false);

        VadResult first = feed(0f);
        VadResult second = feed(0f);

        assertThat(first.getStatus()).isEqualTo(VadStatus.SPEECH_START);
        assertThat(first.getProcessedData()).isNotEmpty();
        assertThat(second.getStatus()).isEqualTo(VadStatus.SPEECH_CONTINUE);
        assertThat(vadService.getPcmData(SESSION_ID)).hasSize(2);
        // manual 模式完全跳过 Silero
        verify(vadModel, never()).infer(any(), any(), any());
    }

    @Test
    void finishSegmentEndsSpeechOnceAndKeepsPcmForStt() {
        vadService.initSession(SESSION_ID, false);
        feed(0f);

        boolean first = vadService.finishSegment(SESSION_ID);
        boolean second = vadService.finishSegment(SESSION_ID);

        assertThat(first).isTrue();
        // 重复的 listen/stop 不能再报告一次收句，否则会多起一轮对话
        assertThat(second).isFalse();
        // 收句不清 pcmData：STT 虚拟线程之后还要读本轮音频
        assertThat(vadService.getPcmData(SESSION_ID)).hasSize(1);
    }

    @Test
    void speechStartRequiresTwoConsecutiveSpeechFrames() {
        vadService.initSession(SESSION_ID);

        assertThat(feed(0.9f).getStatus()).isEqualTo(VadStatus.NO_SPEECH);
        assertThat(feed(0.9f).getStatus()).isEqualTo(VadStatus.SPEECH_START);
    }

    @Test
    void speechEndTrimsTailSilenceProportionally() {
        vadService.initSession(SESSION_ID);
        speakThenAccumulateSilence(6);
        assertThat(vadService.getPcmData(SESSION_ID)).hasSize(9);

        rewindSilenceStart(1100);
        VadResult end = feed(0.1f);

        assertThat(end.getStatus()).isEqualTo(VadStatus.SPEECH_END);
        // 6 个静音帧按 (1100-300)/1100 比例删除，向上取整为 5 帧
        assertThat(vadService.getPcmData(SESSION_ID)).hasSize(4);
    }

    @Test
    void tailKeepLongerThanSilenceKeepsAllFrames() {
        ReflectionTestUtils.setField(vadService, "tailKeepMs", 2000);
        vadService.initSession(SESSION_ID);
        speakThenAccumulateSilence(6);

        rewindSilenceStart(900);
        VadResult end = feed(0.1f);

        assertThat(end.getStatus()).isEqualTo(VadStatus.SPEECH_END);
        assertThat(vadService.getPcmData(SESSION_ID)).hasSize(9);
    }

    @Test
    void trimStopsWhenPcmDataIsExhausted() {
        vadService.initSession(SESSION_ID);
        speakThenAccumulateSilence(6);
        // 计出来的待删帧数超过实际缓存帧数时只能删到空，不能越界
        ReflectionTestUtils.setField(vadState(), "silenceFrameCount", 50);

        rewindSilenceStart(1100);
        VadResult end = feed(0.1f);

        assertThat(end.getStatus()).isEqualTo(VadStatus.SPEECH_END);
        assertThat(vadService.getPcmData(SESSION_ID)).isEmpty();
    }

    @Test
    void longSilenceResetsGruStateAndSpeechCanStartAgain() {
        vadService.initSession(SESSION_ID);
        for (int i = 0; i < 30; i++) {
            assertThat(feed(0.1f).getStatus()).isEqualTo(VadStatus.NO_SPEECH);
        }

        inboundStateHeads.clear();
        feed(0.1f);
        // 第 31 帧推理拿到的是刚被清零的隐状态
        assertThat(inboundStateHeads).isNotEmpty();
        assertThat(inboundStateHeads.get(0)).isEqualTo(0.0f);

        feed(0.9f);
        assertThat(feed(0.9f).getStatus()).isEqualTo(VadStatus.SPEECH_START);
    }

    @Test
    void roleWithoutThresholdsFallsBackToDefaults() {
        when(roleService.getBO(ROLE_ID)).thenReturn(new RoleBO());
        vadService.initSession(SESSION_ID);

        // 0.35 低于默认起播阈值 0.4，连喂两帧也不起播
        assertThat(feed(0.35f).getStatus()).isEqualTo(VadStatus.NO_SPEECH);
        assertThat(feed(0.35f).getStatus()).isEqualTo(VadStatus.NO_SPEECH);
        assertThat(feed(0.45f).getStatus()).isEqualTo(VadStatus.SPEECH_START);
    }

    @Test
    void sessionWithoutDeviceUsesDefaultThresholds() {
        WebSocketSession bare = new WebSocketSession(springSession);
        when(sessionManager.getSession(SESSION_ID)).thenReturn(bare);
        vadService.initSession(SESSION_ID);

        assertThat(feed(0.35f).getStatus()).isEqualTo(VadStatus.NO_SPEECH);
        assertThat(feed(0.35f).getStatus()).isEqualTo(VadStatus.NO_SPEECH);
        assertThat(feed(0.45f).getStatus()).isEqualTo(VadStatus.SPEECH_START);
    }

    /** 起播后再喂 silenceFrames 帧未超时的静音，此时 pcmData 为 1 个起播块 + 2 个语音块 + 静音块 */
    private void speakThenAccumulateSilence(int silenceFrames) {
        feed(0.9f);
        feed(0.9f);
        feed(0.9f);
        feed(0.9f);
        for (int i = 0; i < silenceFrames; i++) {
            assertThat(feed(0.1f).getStatus()).isEqualTo(VadStatus.SPEECH_CONTINUE);
        }
    }

    private VadResult feed(float probability) {
        this.speechProbability = probability;
        return vadService.processAudio(SESSION_ID, loudOpusFrame());
    }

    /** VAD 用挂钟计静音时长，测试把静音起点回拨到确定毫秒数，避免真实等待 */
    private void rewindSilenceStart(long millis) {
        ReflectionTestUtils.setField(vadState(), "silenceTime", System.currentTimeMillis() - millis);
    }

    private Object vadState() {
        Map<?, ?> states = (Map<?, ?>) ReflectionTestUtils.getField(vadService, "states");
        return states.get(SESSION_ID);
    }

    private static DeviceBO device() {
        DeviceBO device = new DeviceBO();
        device.setDeviceId("aa:bb:cc:11:22:33");
        device.setRoleId(ROLE_ID);
        return device;
    }

    private static RoleBO role(float speechThreshold, float silenceThreshold, int silenceMs) {
        RoleBO role = new RoleBO();
        role.setVadSpeechTh(speechThreshold);
        role.setVadSilenceTh(silenceThreshold);
        role.setVadEnergyTh(0.001f);
        role.setVadSilenceMs(silenceMs);
        return role;
    }

    /** 一帧 60ms 方波，解码后能量远高于门限，保证 hasEnergy 恒真 */
    private static byte[] loudOpusFrame() {
        byte[] pcm = new byte[AudioUtils.FRAME_SIZE * 2];
        for (int i = 0; i < AudioUtils.FRAME_SIZE; i++) {
            short sample = (short) ((i / 8) % 2 == 0 ? 8000 : -8000);
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return new OpusProcessor().pcmToOpus(pcm, false).get(0);
    }
}

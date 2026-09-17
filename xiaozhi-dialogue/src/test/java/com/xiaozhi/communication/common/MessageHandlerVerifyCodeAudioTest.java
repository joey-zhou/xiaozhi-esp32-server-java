package com.xiaozhi.communication.common;

import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.AudioUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住未绑定设备验证码语音的存取：dialogue 合成、server 下发给前端、下一次连接再播一遍，
 * 三个动作分处不同进程与工作目录，音频必须经 StorageService 出入，路径不能直接落本地文件名。
 */
@ExtendWith(MockitoExtension.class)
class MessageHandlerVerifyCodeAudioTest {

    private static final String SESSION_ID = "verify-code-session";
    private static final String DEVICE_ID = "94:a9:90:2b:dd:18";
    private static final String CODE = "246813";
    private static final String AUDIO_FILE_NAME = "8f3ac1d24e1b4f0d9a7c5e6b2d10f4a3.wav";
    private static final String STORED_URL = "https://bucket.cos.example.com/audio/verifycode/" + AUDIO_FILE_NAME;
    /** 验证码流程整段跑在虚拟线程上，断言要留出调度与播放时间 */
    private static final long ASYNC_TIMEOUT_MS = 3000;

    @TempDir
    Path tempDir;

    @Mock
    private SessionManager sessionManager;
    @Mock
    private DeviceService deviceService;
    @Mock
    private TtsServiceFactory ttsFactory;
    @Mock
    private TtsService ttsService;
    @Mock
    private StorageServiceFactory storageServiceFactory;
    @Mock
    private StorageService storageService;
    @Mock
    private MessageSender messageService;
    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    private MessageHandler messageHandler;
    private String originalAudioPath;

    @BeforeEach
    void setUp() {
        // AUDIO_PATH 是全局静态字段，生产期由配置注入，测试改完必须还原，否则污染同 JVM 的其他测试
        originalAudioPath = AudioUtils.AUDIO_PATH;
        AudioUtils.AUDIO_PATH = tempDir.resolve("audio").toString() + "/";

        messageHandler = new MessageHandler();
        ReflectionTestUtils.setField(messageHandler, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(messageHandler, "deviceService", deviceService);
        ReflectionTestUtils.setField(messageHandler, "ttsFactory", ttsFactory);
        ReflectionTestUtils.setField(messageHandler, "storageServiceFactory", storageServiceFactory);
        ReflectionTestUtils.setField(messageHandler, "messageService", messageService);

        lenient().when(springSession.getId()).thenReturn(SESSION_ID);
        lenient().when(springSession.isOpen()).thenReturn(true);
        WebSocketSession session = new WebSocketSession(springSession);
        lenient().when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        lenient().when(ttsFactory.getDefaultTtsService()).thenReturn(ttsService);
        lenient().when(storageServiceFactory.getStorageService()).thenReturn(storageService);
    }

    @AfterEach
    void tearDown() {
        AudioUtils.AUDIO_PATH = originalAudioPath;
    }

    @Test
    void firstCodeUploadsAudioAndPersistsStoredPath() throws Exception {
        stubGeneratedCode(null);
        Path synthesized = synthesizedAudio();
        when(ttsService.textToSpeech(anyString())).thenReturn(synthesized);
        when(storageService.upload(any(Path.class), anyString())).thenReturn(STORED_URL);

        messageHandler.handleUnboundDevice(SESSION_ID, unboundDevice());

        // 入库的必须是 upload 的返回值，本地文件名只在存储键里出现
        verify(deviceService, timeout(ASYNC_TIMEOUT_MS))
                .updateCodeAudioPath(DEVICE_ID, SESSION_ID, CODE, STORED_URL);
        ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
        verify(storageService).upload(eq(synthesized), objectKey.capture());
        assertThat(objectKey.getValue()).isEqualTo(AudioUtils.AUDIO_PATH + "verifycode/" + AUDIO_FILE_NAME);

        // 本次合成的字节在上传前已读出，不再回头下载一遍
        verify(storageServiceFactory, never()).downloadFrom(anyString());
        verify(messageService, timeout(ASYNC_TIMEOUT_MS)).sendTtsMessage(any(), eq(CODE), eq("sentence_start"));
    }

    @Test
    void uploadFailureStillPlaysAndLeavesAudioPathEmpty() throws Exception {
        stubGeneratedCode(null);
        when(ttsService.textToSpeech(anyString())).thenReturn(synthesizedAudio());
        when(storageService.upload(any(Path.class), anyString())).thenThrow(new IOException("对象存储不可用"));

        messageHandler.handleUnboundDevice(SESSION_ID, unboundDevice());

        // 存不进共享存储也要把验证码播出去，绑定流程不能因此断掉
        verify(messageService, timeout(ASYNC_TIMEOUT_MS)).sendTtsMessage(any(), eq(CODE), eq("sentence_start"));
        // 路径留空，下次连接重新合成，不留一个别的进程读不到的值
        verify(deviceService, never()).updateCodeAudioPath(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void existingCodeReadsAudioThroughStorage() throws Exception {
        stubGeneratedCode(STORED_URL);
        when(storageServiceFactory.downloadFrom(STORED_URL)).thenReturn(wavBytes());

        messageHandler.handleUnboundDevice(SESSION_ID, unboundDevice());

        verify(messageService, timeout(ASYNC_TIMEOUT_MS)).sendTtsMessage(any(), eq(CODE), eq("sentence_start"));
        // 已有录音直接复用，不重复合成、不重复写库
        verify(ttsService, never()).textToSpeech(anyString());
        verify(deviceService, never()).updateCodeAudioPath(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unreadableExistingAudioSkipsPlayback() {
        stubGeneratedCode(STORED_URL);
        when(storageServiceFactory.downloadFrom(STORED_URL)).thenReturn(null);

        messageHandler.handleUnboundDevice(SESSION_ID, unboundDevice());

        verify(storageServiceFactory, timeout(ASYNC_TIMEOUT_MS)).downloadFrom(STORED_URL);
        // 读不到就不要开播，否则设备收到一段没有声音的 tts start/stop
        verify(messageService, after(200).never()).sendTtsMessage(any(), any(), any());
    }

    private void stubGeneratedCode(String audioPath) {
        VerifyCodeBO verifyCode = new VerifyCodeBO();
        verifyCode.setDeviceId(DEVICE_ID);
        verifyCode.setCode(CODE);
        verifyCode.setAudioPath(audioPath);
        when(deviceService.generateCode(DEVICE_ID, SESSION_ID, "esp32")).thenReturn(verifyCode);
    }

    private static DeviceBO unboundDevice() {
        DeviceBO device = new DeviceBO();
        device.setDeviceId(DEVICE_ID);
        device.setType("esp32");
        return device;
    }

    /** TTS 落在音频目录下的合成结果，180ms 静音，播放器按 60ms 分块 */
    private Path synthesizedAudio() {
        Path path = Path.of(AudioUtils.AUDIO_PATH, AUDIO_FILE_NAME);
        AudioUtils.saveAsWav(path, new byte[3840 * 3]);
        return path;
    }

    private byte[] wavBytes() throws IOException {
        return Files.readAllBytes(synthesizedAudio());
    }
}

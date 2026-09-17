package com.xiaozhi.dialogue;

import com.xiaozhi.common.SerialTaskRegistry;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.audio.VadService;
import com.xiaozhi.dialogue.runtime.UserSpeechAudio;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户音频的落盘与上传整段异步执行：这段磁盘 I/O 加网络 I/O 不能压在 STT 终稿到 LLM 请求之间，
 * 否则直接叠加在每轮的首字延迟上。
 * 结果回填到本轮的 UserSpeechAudio，失败与"没有音频"也必须回填，否则等结果的调用方只能干等到超时。
 */
@ExtendWith(MockitoExtension.class)
class DialogueServiceUserAudioTest {

    private static final String SESSION_ID = "s-audio";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(3);
    /** 16kHz 单声道 16bit 下正好 0.1 秒 */
    private static final int PCM_BYTES = 3200;

    @TempDir
    Path audioDir;

    @Mock
    private VadService vadService;
    @Mock
    private StorageServiceFactory storageServiceFactory;
    @Mock
    private StorageService storageService;
    @Mock
    private org.springframework.web.socket.WebSocketSession springSession;

    @InjectMocks
    private DialogueService dialogueService;

    private ChatSession session;

    @BeforeEach
    void setUp() {
        lenient().when(springSession.getId()).thenReturn(SESSION_ID);
        session = new WebSocketSession(springSession);
    }

    @Test
    void writesAndUploadsAsynchronously() throws Exception {
        when(vadService.getPcmData(SESSION_ID)).thenReturn(List.of(new byte[PCM_BYTES]));
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.upload(any(Path.class), anyString()))
                .thenReturn("https://oss.example.com/audio/user.wav");

        Path wav = audioDir.resolve("user.wav");
        UserSpeechAudio audio = new UserSpeechAudio(wav);
        dialogueService.saveUserAudio(session, audio);

        // PCM 必须在调用线程取走：下一轮 SPEECH_START 会清空 VAD 缓冲
        verify(vadService).getPcmData(SESSION_ID);

        assertThat(audio.awaitStoredPath(AWAIT_TIMEOUT)).isEqualTo("https://oss.example.com/audio/user.wav");
        assertThat(Files.exists(wav)).isTrue();
        assertThat(audio.duration()).isCloseTo(0.1, within(1e-6));
    }

    /** 上传失败退回本地路径，路径不能丢 */
    @Test
    void keepsLocalPathWhenUploadFails() throws Exception {
        when(vadService.getPcmData(SESSION_ID)).thenReturn(List.of(new byte[PCM_BYTES]));
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.upload(any(Path.class), anyString())).thenThrow(new IOException("对象存储不可用"));

        Path wav = audioDir.resolve("user.wav");
        UserSpeechAudio audio = new UserSpeechAudio(wav);
        dialogueService.saveUserAudio(session, audio);

        assertThat(audio.awaitStoredPath(AWAIT_TIMEOUT)).isEqualTo(wav.toString());
    }

    /** 本轮没有音频也要回填，否则声纹注册这类等结果的调用方会一直等到超时 */
    @Test
    void completesEvenWithoutAudio() {
        when(vadService.getPcmData(SESSION_ID)).thenReturn(List.of());

        Path wav = audioDir.resolve("user.wav");
        UserSpeechAudio audio = new UserSpeechAudio(wav);
        dialogueService.saveUserAudio(session, audio);

        assertThat(audio.awaitStoredPath(AWAIT_TIMEOUT)).isNull();
        assertThat(Files.exists(wav)).isFalse();
    }

    /**
     * 落库排在落盘之后：两者共用会话串行键，后入队的落库任务执行时路径一定已回填。
     * 这条不成立的话，异步化就会让 sys_message.audioPath 丢路径。
     */
    @Test
    void laterTaskOnSameSessionSeesStoredPath() throws Exception {
        when(vadService.getPcmData(SESSION_ID)).thenReturn(List.of(new byte[PCM_BYTES]));
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(storageService.upload(any(Path.class), anyString())).thenReturn("audio/user.wav");

        UserSpeechAudio audio = new UserSpeechAudio(audioDir.resolve("user.wav"));
        dialogueService.saveUserAudio(session, audio);

        // 模拟 DialogueListener.onDialogueTurn：同一个串行键上后入队的落库任务
        CompletableFuture<String> persisted = new CompletableFuture<>();
        SerialTaskRegistry.submit(SESSION_ID, () -> persisted.complete(audio.storedPath()));

        assertThat(persisted.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo("audio/user.wav");
    }
}

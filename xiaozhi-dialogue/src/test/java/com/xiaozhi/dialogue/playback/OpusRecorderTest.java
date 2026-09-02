package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.audio.AecService;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.OpusProcessor;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 回复录音落盘的硬约束：
 * granule position 必须逐帧累加写入，恒为 0 会被符合规范的解码器（ffmpeg / 浏览器）
 * 按 end-trim 裁成 0 时长；句间与暂停下发的静音帧只喂 AEC，写进录音会把每条回复撑长。
 */
class OpusRecorderTest {

    @TempDir
    private Path tempDir;

    private ChatSession session;
    private AecService aecService;
    private OpusRecorder recorder;

    @BeforeEach
    void setUp() {
        session = mock(ChatSession.class);
        aecService = mock(AecService.class);
        when(session.getAudioPath(any(), any()))
                .thenAnswer(inv -> tempDir.resolve(((Instant) inv.getArgument(1)).toEpochMilli() + ".ogg"));
        recorder = new OpusRecorder(session, mock(MessageService.class), aecService, mock(StorageServiceFactory.class));
    }

    @Test
    void granulePositionAccumulatesAcrossFrames() throws IOException {
        Instant createdAt = Instant.ofEpochMilli(1_700_000_000_000L);
        recorder.setAssistantMessageCreatedAt(createdAt);
        List<byte[]> frames = opusFrames(3);

        frames.forEach(frame -> recorder.onSendOpusFrame(frame, 1000L));
        recorder.onSendStop();

        long samplesPerFrame = new OpusAudioData(frames.get(0)).getNumberOfSamples();
        assertThat(samplesPerFrame).isPositive();
        verify(session).getAudioPath(eq(MessageBO.SENDER_ASSISTANT), eq(createdAt));
        assertThat(granulePositions(audioPath(createdAt)))
                .containsExactly(samplesPerFrame, samplesPerFrame * 2, samplesPerFrame * 3);
    }

    @Test
    void silenceFramesAreFedToAecButNotRecorded() throws IOException {
        Instant createdAt = Instant.ofEpochMilli(1_700_000_001_000L);
        recorder.setAssistantMessageCreatedAt(createdAt);
        byte[] silence = OpusProcessor.silenceFrame();

        recorder.onSendOpusFrame(opusFrames(1).get(0), 1000L);
        recorder.onSendSilenceFrame(silence, 1060L);
        recorder.onSendSilenceFrame(silence, 1120L);
        recorder.onSendStop();

        // 三帧都作为 AEC 参考出让，只有真帧写进录音
        verify(aecService, times(3)).feedReference(any(), any(), anyLong());
        verify(aecService).feedReference(any(), eq(silence), eq(1060L));
        assertThat(granulePositions(audioPath(createdAt))).hasSize(1);
    }

    @Test
    void newStartClosesPreviousFileAndRestartsGranule() throws IOException {
        Instant first = Instant.ofEpochMilli(1_700_000_002_000L);
        recorder.setAssistantMessageCreatedAt(first);
        recorder.onSendOpusFrame(opusFrames(1).get(0), 1000L);

        // 上一条回复的文件没关就来了新一轮，必须先收尾再开新文件
        Instant second = Instant.ofEpochMilli(1_700_000_003_000L);
        recorder.setAssistantMessageCreatedAt(second);
        recorder.onSendStart();
        List<byte[]> frames = opusFrames(2);
        frames.forEach(frame -> recorder.onSendOpusFrame(frame, 2000L));
        recorder.onSendStop();

        long samplesPerFrame = new OpusAudioData(frames.get(0)).getNumberOfSamples();
        assertThat(granulePositions(audioPath(first))).containsExactly(samplesPerFrame);
        assertThat(granulePositions(audioPath(second)))
                .containsExactly(samplesPerFrame, samplesPerFrame * 2);
    }

    @Test
    void noFileCreatedWithoutAssistantTimestamp() throws IOException {
        // 问候语、告别语没有对应的 assistant 消息，不建文件但仍要喂 AEC
        recorder.onSendOpusFrame(opusFrames(1).get(0), 1000L);
        recorder.onSendStop();

        verify(session, never()).getAudioPath(any(), any());
        verify(aecService).feedReference(any(), any(), anyLong());
        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void closeWithoutOpenFileIsNoOp() throws IOException {
        recorder.closeOpusFile();
        recorder.closeOpusFile();

        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files).isEmpty();
        }
    }

    private Path audioPath(Instant createdAt) {
        return tempDir.resolve(createdAt.toEpochMilli() + ".ogg");
    }

    /** 按写入顺序读回每个音频包的 granule position */
    private static List<Long> granulePositions(Path path) throws IOException {
        assertThat(path).exists();
        List<Long> positions = new ArrayList<>();
        try (OpusFile file = new OpusFile(path.toFile())) {
            OpusAudioData data;
            while ((data = file.getNextAudioPacket()) != null) {
                positions.add(data.getGranulePosition());
            }
        }
        return positions;
    }

    /** 由真实编码器生成 count 个 60ms 非静音 Opus 帧 */
    private static List<byte[]> opusFrames(int count) {
        int samples = AudioUtils.FRAME_SIZE * count;
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            short value = (short) (8000 * Math.sin(2 * Math.PI * 440 * i / AudioUtils.SAMPLE_RATE));
            pcm[i * 2] = (byte) (value & 0xFF);
            pcm[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        List<byte[]> frames = new OpusProcessor().pcmToOpus(pcm, false);
        assertThat(frames).hasSize(count);
        return frames;
    }
}

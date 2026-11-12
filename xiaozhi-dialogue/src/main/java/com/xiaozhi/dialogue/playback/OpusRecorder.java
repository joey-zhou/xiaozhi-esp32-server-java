package com.xiaozhi.dialogue.playback;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.audio.AecService;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.common.SerialTaskRegistry;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.DateUtils;
import com.xiaozhi.utils.OpusProcessor;
import lombok.extern.slf4j.Slf4j;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusInfo;
import org.gagravarr.opus.OpusTags;
import com.xiaozhi.common.model.bo.MessageBO;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Opus 音频录制组件：将播放器发送给设备的 Opus 帧同时写入 OGG/Opus 文件。
 *
 * 通过组合模式注入 Player，替代原 PlayerWithOpusFile 的继承方式。
 * Player 在 sendOpusFrame/sendStart/sendStop 中回调本组件的对应方法。
 * <p>
 * 发帧在播放线程，打断时的收尾在识别回调线程，两边同时进来：文件相关的字段都由实例锁保护。
 */
@Slf4j
public class OpusRecorder {

    private final ChatSession session;
    private final MessageService messageService;
    private final AecService aecService;
    private final StorageServiceFactory storageServiceFactory;

    private Path audioPath;
    private OpusFile opusFile;
    private Instant opusFileCreatedAt;
    // Opus granule position 累加器（48kHz 采样单位）。末页 granule 决定解码器输出样本数（end-trim），
    // 若不累加、恒为 0，符合规范的解码器（ffmpeg / 浏览器）会裁掉整段音频，表现为时长 0 / 只放末尾。
    private long granulePosition;
    // 最近一个已收尾文件对应的助手消息。打断收尾后播放线程可能还会落下一帧，
    // 不能为同一条消息重开文件，否则同一路径被截成一帧的残片、再以残片更新消息音频
    private Instant closedCreatedAt;

    private Instant assistantMessageCreatedAt;

    public OpusRecorder(ChatSession session, MessageService messageService, AecService aecService, StorageServiceFactory storageServiceFactory) {
        this.session = session;
        this.messageService = messageService;
        this.aecService = aecService;
        this.storageServiceFactory = storageServiceFactory;
    }

    public synchronized Instant getAssistantMessageCreatedAt() {
        return assistantMessageCreatedAt;
    }

    public synchronized void setAssistantMessageCreatedAt(Instant assistantMessageCreatedAt) {
        this.assistantMessageCreatedAt = assistantMessageCreatedAt;
    }

    public void onSendStart() {
        closeOpusFile();
    }

    /**
     * 本会话是否在做服务端 AEC。没做的话下发帧不必带着编码前的 PCM，留着只是白占内存
     */
    public boolean needsReferencePcm() {
        return aecService != null && aecService.isActive(session.getSessionId());
    }

    /**
     * @param referencePcm 该帧编码前的 PCM，缓存命中直读的帧没有源 PCM，为 null
     */
    public void onSendOpusFrame(byte[] opusFrame, byte[] referencePcm, long timestamp) {
        if (aecService != null) {
            aecService.feedReference(session.getSessionId(), opusFrame, referencePcm, timestamp);
        }

        synchronized (this) {
            if (opusFile == null && assistantMessageCreatedAt != null
                    && !assistantMessageCreatedAt.equals(closedCreatedAt)) {
                openOpusFile();
            }
            if (opusFile != null) {
                OpusAudioData audioData = new OpusAudioData(opusFrame);
                granulePosition += audioData.getNumberOfSamples();
                audioData.setGranulePosition(granulePosition);
                opusFile.writeAudioData(audioData);
            }
        }
    }

    /**
     * 不属于任何一条助手消息的帧（问候、告别、故障提示）只作为 AEC 参考，不写入录音，
     * 否则会续写进上一条助手消息的录音里
     */
    public void onSendUnrecordedFrame(byte[] opusFrame, byte[] referencePcm, long timestamp) {
        if (aecService != null) {
            aecService.feedReference(session.getSessionId(), opusFrame, referencePcm, timestamp);
        }
    }

    /**
     * 静音帧只作为 AEC 参考，不写入录音。静音帧的源 PCM 是常量，不必解码
     */
    public void onSendSilenceFrame(byte[] opusFrame, long timestamp) {
        if (aecService != null) {
            aecService.feedReference(session.getSessionId(), opusFrame, OpusProcessor.silencePcm(), timestamp);
        }
    }

    public void onSendStop() {
        closeOpusFile();
    }

    /** 调用方持有实例锁 */
    private void openOpusFile() {
        opusFileCreatedAt = assistantMessageCreatedAt;
        granulePosition = 0;
        audioPath = session.getAudioPath(MessageBO.SENDER_ASSISTANT, opusFileCreatedAt);
        try {
            Files.createDirectories(audioPath.getParent());
            FileOutputStream fos = new FileOutputStream(audioPath.toFile());
            OpusInfo oi = new OpusInfo();
            oi.setSampleRate(AudioUtils.SAMPLE_RATE);
            oi.setNumChannels(AudioUtils.CHANNELS);
            oi.setPreSkip(0);

            OpusTags ot = new OpusTags();
            ot.addComment("TITLE", "Xiaozhi TTS Audio");
            ot.addComment("ARTIST", "Xiaozhi ESP32 Server");

            opusFile = new OpusFile(fos, oi, ot);
        } catch (IOException ex) {
            log.error("无法创建Opus音频文件 - SessionId: {}", session.getSessionId(), ex);
        }
    }

    /**
     * 关闭文件同步做，时长回读、上传与写库排进会话落库队列。
     * 后者必须让开 tts stop 的下发路径，且不能读 audioPath/opusFileCreatedAt 字段（下一轮会覆盖）。
     * 文件在锁内摘下、锁外关闭：摘下之后播放线程已碰不到它，落盘不必挡住发帧。
     */
    public void closeOpusFile() {
        OpusFile file;
        Path closedPath;
        Instant createdAt;
        synchronized (this) {
            if (opusFile == null) {
                return;
            }
            file = opusFile;
            opusFile = null;
            closedPath = audioPath;
            createdAt = opusFileCreatedAt;
            closedCreatedAt = createdAt;
        }
        Persona persona = session.getPersona();
        try {
            file.close();
            log.info("Opus音频文件已生成: {}", closedPath);
        } catch (Exception e) {
            log.error("无法关闭Opus音频文件: {}", closedPath, e);
            return;
        }
        if (persona == null || createdAt == null || closedPath == null) {
            return;
        }
        Conversation conversation = persona.getConversation();
        SerialTaskRegistry.submit(conversation.getSessionId(),
                () -> updateMessage(conversation, closedPath, createdAt));
    }

    private void updateMessage(Conversation conversation, Path closedPath, Instant createdAt) {
        BigDecimal duration = BigDecimal.valueOf(AudioUtils.getAudioDuration(closedPath));

        String storedPath = closedPath.toString();
        try {
            storedPath = storageServiceFactory.getStorageService().upload(closedPath, closedPath.toString());
        } catch (Exception e) {
            log.warn("上传AI回复音频失败，保留本地路径: {}", closedPath, e);
        }

        messageService.updateAssistantAudio(
            conversation.getOwnerId(),
            conversation.getRoleId(),
            DateUtils.toDateTime(createdAt.truncatedTo(ChronoUnit.SECONDS)),
            storedPath,
            duration
        );
    }
}

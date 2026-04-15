package com.xiaozhi.dialogue.playback;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.audio.AecService;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.AudioUtils;
import io.jsonwebtoken.lang.Assert;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusInfo;
import org.gagravarr.opus.OpusTags;
import com.xiaozhi.common.model.bo.MessageBO;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Opus 音频录制组件：将播放器发送给设备的 Opus 帧同时写入 OGG/Opus 文件。
 *
 * 通过组合模式注入 Player，替代原 PlayerWithOpusFile 的继承方式。
 * Player 在 sendOpusFrame/sendStart/sendStop 中回调本组件的对应方法。
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

    @Getter
    @Setter
    private Instant assistantMessageCreatedAt;

    public OpusRecorder(ChatSession session, MessageService messageService, AecService aecService, StorageServiceFactory storageServiceFactory) {
        this.session = session;
        this.messageService = messageService;
        this.aecService = aecService;
        this.storageServiceFactory = storageServiceFactory;
    }

    public void onSendStart() {
        if (opusFile != null) {
            closeOpusFile();
        }
    }

    public void onSendOpusFrame(byte[] opusFrame) {
        if (aecService != null && aecService.isEnabled()) {
            aecService.feedReference(session.getSessionId(), opusFrame);
        }

        if (opusFile == null && assistantMessageCreatedAt != null) {
            openOpusFile();
        }
        if (opusFile != null) {
            opusFile.writeAudioData(new OpusAudioData(opusFrame));
        }
    }

    public void onSendStop() {
        closeOpusFile();
    }

    private void openOpusFile() {
        opusFileCreatedAt = assistantMessageCreatedAt;
        audioPath = session.getAudioPath(MessageBO.SENDER_ASSISTANT, opusFileCreatedAt);
        try {
            Files.createDirectories(audioPath.getParent());
            try {
                FileOutputStream fos = new FileOutputStream(audioPath.toFile());
                OpusInfo oi = new OpusInfo();
                oi.setSampleRate(AudioUtils.SAMPLE_RATE);
                oi.setNumChannels(AudioUtils.CHANNELS);
                oi.setPreSkip(0);

                OpusTags ot = new OpusTags();
                ot.addComment("TITLE", "Xiaozhi TTS Audio");
                ot.addComment("ARTIST", "Xiaozhi ESP32 Server");

                opusFile = new OpusFile(fos, oi, ot);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException ex) {
            log.error("无法创建保存Opus音频文件的目录 - SessionId: {}", session.getSessionId());
            log.error("无法创建保存Opus音频文件的目录", ex);
        }
    }

    public void closeOpusFile() {
        if (opusFile == null) {
            return;
        }
        try {
            opusFile.close();
            log.info("Opus音频文件已生成: {}", audioPath);
            opusFile = null;
            updateMessage();
        } catch (IOException e) {
            log.error("无法关闭Opus音频文件!", e);
        }
    }

    private void updateMessage() {
        Persona persona = session.getPersona();
        if (persona == null || opusFileCreatedAt == null || audioPath == null) {
            return;
        }
        Conversation conversation = persona.getConversation();
        Assert.notNull(conversation);

        BigDecimal duration = BigDecimal.valueOf(AudioUtils.getAudioDuration(audioPath));

        String storedPath = audioPath.toString();
        try {
            storedPath = storageServiceFactory.getStorageService().upload(audioPath, audioPath.toString());
        } catch (Exception e) {
            log.warn("上传AI回复音频失败，保留本地路径: {}", audioPath, e);
        }

        messageService.updateAssistantAudio(
            conversation.device().getDeviceId(),
            conversation.role().getRoleId(),
            LocalDateTime.ofInstant(opusFileCreatedAt.truncatedTo(ChronoUnit.SECONDS), ZoneId.systemDefault()),
            storedPath,
            duration
        );
    }
}

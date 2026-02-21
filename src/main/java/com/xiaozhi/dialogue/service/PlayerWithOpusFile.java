package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.aec.AecService;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.AudioUtils;
import io.jsonwebtoken.lang.Assert;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusInfo;
import org.gagravarr.opus.OpusTags;
import org.springframework.ai.chat.messages.MessageType;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 对播放器增强功能：在播放器播放的过程中将音频生成文件
 * TODO 思考：改成Wrapper的模式可能比继承更好。
 */
@Slf4j
public abstract class PlayerWithOpusFile extends Player {

    private Path audioPath = null;
    private OpusFile opusFile = null;
    private Instant opusFileCreatedAt = null;

    @Getter
    @Setter
    private Instant assistantMessageCreatedAt = null;
    private SysMessageService sysMessageService;
    private AecService aecService;

    protected PlayerWithOpusFile(ChatSession session, MessageService messageService,
                                  SysMessageService sysMessageService, AecService aecService) {
        super(session, messageService);
        this.sysMessageService = sysMessageService;
        this.aecService = aecService;
    }

    @Override
    protected void sendStart() {
        super.sendStart();

        if (opusFile != null) {
            closeOpusFile();
        }

    }

    private void openOpusFile() {
        // 快照时间戳，防止外部在文件关闭前将其置 null（如发送告别语时）
        opusFileCreatedAt = assistantMessageCreatedAt;
        // 生成音频文件路径
        audioPath = session.getAudioPath(MessageType.ASSISTANT.getValue(), opusFileCreatedAt);
        // 将audioPath的尾缀转换为 ".ogg"，不要使用绝对路径
        audioPath = audioPath.getParent().resolve(audioPath.getFileName().toString().replace(".wav", ".ogg"));
        try {
            // 确保目录存在
            Files.createDirectories(audioPath.getParent());

            // 使用vorbis-java和concentus库将opusFrames保存为Ogg Opus文件
            try {
                FileOutputStream fos = new FileOutputStream(audioPath.toFile());
                // 创建OpusInfo对象，设置基本参数
                OpusInfo oi = new OpusInfo();
                oi.setSampleRate(AudioUtils.SAMPLE_RATE);
                oi.setNumChannels(AudioUtils.CHANNELS);
                oi.setPreSkip(0); // 通常设置为0或编码器推荐值

                // 创建OpusTags对象，可以添加元数据
                OpusTags ot = new OpusTags();
                ot.addComment("TITLE", "Xiaozhi TTS Audio");
                ot.addComment("ARTIST", "Xiaozhi ESP32 Server");

                // 创建OpusFile用于写入
                opusFile = new OpusFile(fos, oi, ot);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException ex) {
            log.error("无法创建保存Opus音频文件的目录 - SessionId: {}", session.getSessionId());
            log.error("无法创建保存Opus音频文件的目录 ", ex);
        }
    }

    /**
     * @param opusFrame opus 音频帧
     */
    protected void sendOpusFrame(byte[] opusFrame) {
        super.sendOpusFrame(opusFrame);

        // 将发送给设备的 Opus 帧同时喂给 AEC 作为参考信号
        if (aecService != null && aecService.isEnabled()) {
            aecService.feedReference(session.getSessionId(), opusFrame);
        }

        if(opusFile == null && assistantMessageCreatedAt != null){
            openOpusFile();
        }
        if(opusFile!=null){
            // 创建OpusAudioData并写入文件
            OpusAudioData audioData = new OpusAudioData(opusFrame);
            opusFile.writeAudioData(audioData);
        }

    }

    protected void sendStop() {
        super.sendStop();
        closeOpusFile();
    }

    protected void closeOpusFile() {
        if (opusFile == null) {
            return;
        }
        // 关闭文件以确保所有数据都被写入
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
        if (persona == null) {
            return;
        }
        Conversation conversation = persona.getConversation();
        Assert.notNull(conversation);

        String deviceId = conversation.device().getDeviceId();
        Integer roleId = conversation.role().getRoleId();
        String sender = MessageType.ASSISTANT.getValue();

        sysMessageService.updateMessageByAudioFile(deviceId, roleId, sender, conver2Datetime(opusFileCreatedAt), audioPath.toString());
    }

    private String conver2Datetime(Instant instant) {
        instant = instant.truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        String datetime = localDateTime.format(DateTimeFormatter.ISO_DATE_TIME).replace(":", "");
        return datetime;
    }
}

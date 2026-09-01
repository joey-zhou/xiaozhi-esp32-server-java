package com.xiaozhi.dialogue;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.dialogue.audio.VadService;
import com.xiaozhi.dialogue.llm.factory.PersonaFactory;
import com.xiaozhi.ai.llm.memory.MessageTimeMetadata;
import com.xiaozhi.ai.llm.service.IntentService;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.common.model.bo.MessageMetadataBO;
import org.springframework.ai.chat.messages.UserMessage;
import com.xiaozhi.dialogue.audio.VadService.VadStatus;
import com.xiaozhi.dialogue.audio.AecService;
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.event.ChatAbortedEvent;
import com.xiaozhi.event.SpeechRecognizedEvent;

import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.OpusProcessor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

import jakarta.annotation.Resource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
/**
 * 对话处理服务
 * 负责处理语音识别和对话生成的业务逻辑
 * 核心对话逻辑已委托给 Persona，DialogueService 主要负责：
 * 1. 音频数据接收与VAD处理
 * 2. STT流式识别的启动与音频流管理
 * 3. 唤醒词处理
 * 4. 对话中止（abort）
 * 5. 监控数据记录
 */
@Slf4j
@Service
public class DialogueService{
    private static final String ABORT_REASON_ASR = "检测到用户说话";
    /** 唤醒词音频的文件名标记，与 user/assistant 区分开 */
    private static final String WAKE_WORD_AUDIO_TAG = "wakeword";

    @Resource
    private PersonaFactory personaFactory;

    @Resource
    private MessageSender messageService;

    @Resource
    private VadService vadService;

    @Resource
    private AecService aecService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private IntentService intentService;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private StorageServiceFactory storageServiceFactory;

    @org.springframework.context.event.EventListener
    public void onApplicationEvent(ChatAbortedEvent event) {
        ChatSession chatSession = sessionManager.getSession(event.getSessionId());
        if (chatSession == null) return;
        abortDialogue(chatSession, event.getReason());
    }

    /**
     * 处理音频数据
     */
    public void processAudioData(ChatSession session, byte[] opusData) {
        processAudioData(session, opusData, 0);
    }

    /**
     * @param echoTimestamp 设备回显的下行帧时间戳，0 表示无；透传给 AEC 做参考对齐
     */
    public void processAudioData(ChatSession session, byte[] opusData, long echoTimestamp) {
        if (session == null || opusData == null || opusData.length == 0) {
            return;
        }
        String sessionId = session.getSessionId();

        try {
            // 如果播放器正在执行后续回调（如告别语播放中），忽略音频数据
            Player player = session.getPlayer();
            if (player != null && player.getFunctionAfterChat() != null) {
                return;
            }

            DeviceBO device = session.getDevice();
            // 如果设备未注册或未绑定，忽略音频数据
            if (device == null || ObjectUtils.isEmpty(device.getRoleId())) {
                return;
            }

            // 处理VAD
            VadService.VadResult vadResult = vadService.processAudio(sessionId, opusData, echoTimestamp);
            if (vadResult == null) {
                // VAD 未初始化，即设备在 listen/start 之前补发的唤醒词音频，只采集不送识别
                session.addWakeWordAudio(opusData);
                return;
            }
            if (vadResult.getStatus() == VadStatus.ERROR || vadResult.getProcessedData() == null) {
                return;
            }

            // 检测到语音活动，更新最后活动时间
            sessionManager.updateLastActivity(sessionId);
            // 根据VAD状态处理
            switch (vadResult.getStatus()) {
                case SPEECH_START:
                    // 启动STT（同步创建音频流），确保流已准备好。打断改由 ASR 首字触发，见 onSttPartialText
                    startStt(session, sessionId, vadResult.getProcessedData());
                    break;

                case SPEECH_CONTINUE:
                    // 语音继续，发送数据到流式识别
                    if (session.getDeviceState() == DeviceState.LISTENING) {
                        session.sendAudioData(vadResult.getProcessedData());
                    }
                    break;

                case SPEECH_END:
                    completeSpeechSegment(session);
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            log.error("处理音频数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 收句：本轮语音到此为止，完成音频流并进入 THINKING 等待 LLM 响应。
     * auto/realtime 由服务端 VAD 的 SPEECH_END 触发，manual 由客户端的 listen/stop 触发。
     */
    public void completeSpeechSegment(ChatSession session) {
        // 收句无条件终结音频流：用户开口后上一轮 TTS 才到达时状态会被改成 SPEAKING，
        // 此时若跳过，STT 侧永远等不到流结束，本轮说的话会整句丢失
        session.completeAudioStream();

        if (session.getDeviceState() == DeviceState.LISTENING) {
            session.transitionTo(DeviceState.THINKING);
        }
    }

    /**
     * ASR 首次识别出文本时打断当前回答。在 STT provider 的识别线程上执行。
     *
     * @param aborted 本轮是否已打断过，保证一轮只打断一次
     */
    private void onSttPartialText(ChatSession session, String partialText, AtomicBoolean aborted) {
        if (!StringUtils.hasText(partialText) || aborted.get()) {
            return;
        }

        // isActive() 覆盖 LLM/TTS/Player 任一层活跃
        Persona persona = session.getPersona();
        if (persona == null || !persona.isActive()) {
            return;
        }

        if (aborted.compareAndSet(false, true)) {
            // 切出识别线程，避免 abort 的耗时动作阻塞后续 ASR 包
            Thread.startVirtualThread(() -> abortDialogue(session, ABORT_REASON_ASR));
        }
    }

    /**
     * 启动语音识别
     * 同步创建音频流（避免竞态条件），然后在虚拟线程中执行 STT 及后续处理
     */
    private void startStt(
            ChatSession session,
            String sessionId,
            byte[] initialAudio) {
        Assert.notNull(session, "session不能为空");

        // 同步部分：先创建音频流和设置状态，避免竞态条件
        // 这样可以确保后续的SPEECH_CONTINUE能正确发送数据
        session.closeAudioStream();
        session.createAudioStream();
        session.transitionTo(DeviceState.LISTENING);
        Sinks.Many<byte[]> turnSink = session.getAudioSinks();

        Thread.startVirtualThread(() -> {
            try {
                // 发送初始音频数据
                if (initialAudio != null && initialAudio.length > 0) {
                    session.sendAudioData(initialAudio);
                }

                if (turnSink == null) {
                    return;
                }

                Persona persona = session.getPersona();
                if (persona == null || persona.getSttService() == null) {
                    return;
                }

                AtomicBoolean aborted = new AtomicBoolean(false);
                var sttResult = persona.getSttService().stream(
                        turnSink.asFlux(),
                        partialText -> onSttPartialText(session, partialText, aborted));

                // 本轮已被新一轮或 abort 取代，结果作废，否则过期文本会触发一轮多余对话
                if (session.getAudioSinks() != turnSink) {
                    return;
                }

                if (sttResult == null || !StringUtils.hasText(sttResult.text())) {
                    return;
                }

                // 发送STT识别结果到设备
                persona.getPlayer().sendStt(sttResult.text());

                // 发布语音识别完成事件
                eventPublisher.publishEvent(new SpeechRecognizedEvent(this, sessionId, sttResult.text(),
                        sttResult.hasEmotion() ? sttResult.emotion() : null));

                // 音频保存
                Instant userInstant = Instant.now();
                Path userAudioPath = session.getAudioPath(MessageBO.SENDER_USER, userInstant);
                session.setUserAudioPath(userAudioPath);
                saveUserAudio(session, userAudioPath);

                handleText(session, sttResult);

            } catch (Exception e) {
                log.error("流式识别错误: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 处理语音唤醒
     */
    public void handleWakeWord(ChatSession session, String text) {
        log.info("检测到唤醒词: {}", text);
        try {
            // 设置为 SPEAKING 状态，在唤醒响应期间忽略 VAD 检测
            session.transitionTo(DeviceState.SPEAKING);

            DeviceBO device = session.getDevice();
            if (device == null) {
                return;
            }

            saveWakeWordAudio(session);
            personaFactory.buildPersona(session).chat(text, false);
        } catch (Exception e) {
            log.error("处理唤醒词失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 统一的文本处理入口：情感标签 → 意图检测 → LLM+TTS
     *
     * @param session 当前会话
     * @param sttResult STT结果（纯文本使用 SttResult.textOnly() 包装）
     */
    public void handleText(ChatSession session, SttResult sttResult) {
        try {
            Persona persona = session.getPersona();

            String text = sttResult.text();

            UserMessage userMessage = buildUserMessage(text, sttResult);

            // 意图检测
            if (intentService.detect(text) == IntentService.Intent.EXIT) {
                sendGoodbyeMessage(session);
                return;
            }

            // LLM+TTS
            try {
                persona.chat(userMessage, true);
            } catch (Exception e) {
                log.error("LLM对话处理失败: {}", e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("处理文本失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构造带结构化元数据与时间戳的 UserMessage。
     * 元数据不在 text 上做前缀拼接，而是走 UserMessage.metadata Map，
     * 由 {@code UserMessageAssembler#assemble(Message)} 在送 LLM 前统一装配。
     *
     * @param text     用户裸文本
     * @param sttResult STT 结果，可能含情绪信息
     */
    private static UserMessage buildUserMessage(String text, SttResult sttResult) {
        MessageMetadataBO metadataBO = MessageMetadataBO.builder()
                .emotion(sttResult.hasEmotion() ? sttResult.emotion() : null)
                .emotionScore(sttResult.hasEmotion() ? sttResult.emotionScore() : null)
                .emotionDegree(sttResult.hasEmotion() ? sttResult.emotionDegree() : null)
                .build();
        Map<String, Object> msgMeta = new HashMap<>();
        // 只要任一字段有值就挂载；全空时不挂，保持 UserMessage.metadata 干净
        if (StringUtils.hasText(metadataBO.getEmotion())) {
            msgMeta.put(MessageMetadataBO.METADATA_KEY, metadataBO);
        }
        UserMessage userMessage = UserMessage.builder().text(text).metadata(msgMeta).build();
        // 消息时间戳（投影层据此拼 [yyyy-MM-ddTHH:mm:ss] 前缀）
        MessageTimeMetadata.setTimeMillis(userMessage, Instant.now());
        return userMessage;
    }

    /**
     * 发送告别语并在播放完成后关闭会话
     * 委托给Persona处理告别流程
     *
     * @param session WebSocket会话
     */
    public void sendGoodbyeMessage(ChatSession session) {
        if (session == null || !session.isAudioChannelOpen()) {
            return;
        }
        Persona persona = session.getPersona();
        if (persona != null) {
            persona.sendGoodbyeMessage();
        } else {
            session.close();
        }
    }

    /**
     * 中止当前对话
     * 先取消Synthesizer的上游Flux订阅，再停止Player。
     * 如果不先取消Synthesizer，SentenceHelper会继续分句并调用player.play(newFlux)，
     * 导致音频重叠或播放被清空后又有新音频进来。
     */
    public void abortDialogue(ChatSession session, String reason) {
        try {
            String sessionId = session.getSessionId();
            log.info("中止对话 - SessionId: {}, Reason: {}", sessionId, reason);

            // ASR 触发的打断不关流：startStt 刚建的新流上正跑着 STT
            if (!ABORT_REASON_ASR.equals(reason)) {
                session.closeAudioStream();
                // abort 后服务端发 tts stop，设备切回聆听，服务端同步为 LISTENING
                session.transitionTo(DeviceState.LISTENING);
            }

            // 先取消语音合成器的上游Flux订阅，停止产生新的音频数据
            Persona persona = session.getPersona();
            if (persona != null && persona.getSynthesizer() != null) {
                persona.getSynthesizer().cancel();
            }

            // 再终止音频播放，清空播放队列
            Player player = session.getPlayer();
            if(player!=null){
                player.stop();
            }

            // 已发送未播放的帧被设备丢弃、不会产生回声，清掉待喂入的 AEC 参考，
            // 否则下一轮开头会被当参考喂入，污染对齐导致回声漏出
            if (aecService != null) {
                aecService.clearReference(session.getSessionId());
            }

            // 无论player是否存在，都需要发送stop消息通知设备进入聆听状态
            // 这是因为设备可能在还未创建player时就发送了abort消息
            messageService.sendTtsMessage(session, null, "stop");

            // 如果在goodbye流程中被打断（functionAfterChat已设置），
            // 需要执行清理回调（关闭session等），并清除回调防止重复执行
            if (player != null) {
                Runnable afterChat = player.getFunctionAfterChat();
                if (afterChat != null) {
                    player.setFunctionAfterChat(null);
                    afterChat.run();
                }
            }
        } catch (Exception e) {
            log.error("中止对话失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存用户音频数据为WAV文件
     */
    /**
     * 落盘唤醒词前置音频。解码与上传都不能拖慢问候语，整段放虚拟线程。
     */
    private void saveWakeWordAudio(ChatSession session) {
        List<byte[]> opusFrames = session.drainWakeWordAudio();
        if (opusFrames.isEmpty()) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                OpusProcessor decoder = new OpusProcessor();
                List<byte[]> pcmFrames = new ArrayList<>(opusFrames.size());
                for (byte[] frame : opusFrames) {
                    pcmFrames.add(decoder.opusToPcm(frame));
                }
                byte[] pcm = AudioUtils.joinPcmFrames(pcmFrames);
                if (pcm.length == 0) {
                    return;
                }
                Path path = session.getAudioPath(WAKE_WORD_AUDIO_TAG, Instant.now());
                AudioUtils.saveAsWav(path, pcm);
                storageServiceFactory.getStorageService().upload(path, path.toString());
                log.debug("唤醒词音频已采集: {}", path);
            } catch (Exception e) {
                log.warn("采集唤醒词音频失败: {}", e.getMessage());
            }
        });
    }

    private void saveUserAudio(ChatSession session, Path path) {
        List<byte[]> pcmFrames = vadService.getPcmData(session.getSessionId());
        byte[] fullPcmData = AudioUtils.joinPcmFrames(pcmFrames);
        if (fullPcmData.length == 0) {
            return;
        }
        AudioUtils.saveAsWav(path, fullPcmData);
        log.debug("用户音频已保存: {}", path);

        // 时长必须在上传前用本地文件算好：上传云存储后本地文件会被删除，
        // 且云端 storedPath（完整 URL）无法当作本地文件读取。
        session.setSttDuration(AudioUtils.getAudioDuration(path));

        // 默认持久化路径为本地相对路径；上传成功则替换为云存储返回的 storedPath（可能是完整 URL）。
        // storedPath 以原始 String 保存，不能经 Path.of 转换——否则 URL 的 "//" 会被规整成 "/"。
        String storedPath = path.toString();
        try {
            storedPath = storageServiceFactory.getStorageService().upload(path, path.toString());
        } catch (Exception e) {
            log.warn("上传用户音频失败，保留本地路径: {}", path, e);
        }
        session.setUserAudioStoredPath(storedPath);
    }

}

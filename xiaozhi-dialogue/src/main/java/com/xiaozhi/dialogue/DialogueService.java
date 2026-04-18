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
import com.xiaozhi.dialogue.playback.Player;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.event.ChatAbortedEvent;
import com.xiaozhi.event.SpeechRecognizedEvent;

import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.utils.AudioUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

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
    private static final String ABORT_REASON_VAD = "检测到vad";

    @Resource
    private PersonaFactory personaFactory;

    @Resource
    private MessageSender messageService;

    @Resource
    private VadService vadService;

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
            VadService.VadResult vadResult = vadService.processAudio(sessionId, opusData);
            if (vadResult == null || vadResult.getStatus() == VadStatus.ERROR
                    || vadResult.getProcessedData() == null) {
                return;
            }

            // 检测到语音活动，更新最后活动时间
            sessionManager.updateLastActivity(sessionId);
            // 根据VAD状态处理
            switch (vadResult.getStatus()) {
                case SPEECH_START:
                    // 先启动STT（同步创建音频流），确保流已准备好
                    startStt(session, sessionId, vadResult.getProcessedData());
                    // 再触发abort停止当前播放中的TTS
                    // 通过Persona.isActive()综合判断整个管道是否活跃（LLM/TTS/Player任一层）
                    Persona persona = session.getPersona();
                    if (persona != null && persona.isActive()) {
                        abortDialogue(session, ABORT_REASON_VAD);
                    }
                    break;

                case SPEECH_CONTINUE:
                    // 语音继续，发送数据到流式识别
                    if (session.getDeviceState() == DeviceState.LISTENING) {
                        session.sendAudioData(vadResult.getProcessedData());
                    }
                    break;

                case SPEECH_END:
                    // 语音结束，完成流式识别；状态切换为 THINKING 等待 LLM 响应
                    if (session.getDeviceState() == DeviceState.LISTENING) {
                        session.completeAudioStream();
                        session.transitionTo(DeviceState.THINKING);
                    }
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            log.error("处理音频数据失败: {}", e.getMessage(), e);
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

        Thread.startVirtualThread(() -> {
            try {
                // 发送初始音频数据
                if (initialAudio != null && initialAudio.length > 0) {
                    session.sendAudioData(initialAudio);
                }

                if (session.getAudioSinks() == null) {
                    return;
                }

                Persona persona = session.getPersona();
                if (persona == null || persona.getSttService() == null) {
                    return;
                }

                var sttResult = persona.getSttService().stream(session.getAudioSinks().asFlux());

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

            // 关闭音频流
            // 注意：当reason是"检测到vad"时，不关闭音频流和重置状态
            // 因为这是用户打断TTS继续说话，startStt已经创建了新的音频流并设置为LISTENING
            if (!ABORT_REASON_VAD.equals(reason)) {
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
    private void saveUserAudio(ChatSession session, Path path) {
        List<byte[]> pcmFrames = vadService.getPcmData(session.getSessionId());
        byte[] fullPcmData = AudioUtils.joinPcmFrames(pcmFrames);
        if (fullPcmData.length == 0) {
            return;
        }
        AudioUtils.saveAsWav(path, fullPcmData);
        log.debug("用户音频已保存: {}", path);

        try {
            String storedPath = storageServiceFactory.getStorageService().upload(path, path.toString());
            session.setUserAudioPath(Path.of(storedPath));
        } catch (Exception e) {
            log.warn("上传用户音频失败，保留本地路径: {}", path, e);
        }
    }

}

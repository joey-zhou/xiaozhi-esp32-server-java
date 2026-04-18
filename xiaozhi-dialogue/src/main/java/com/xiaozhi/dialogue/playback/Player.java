package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.Speech;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.enums.DeviceState;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.OpusProcessor;
import io.jsonwebtoken.lang.Assert;
import lombok.*;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
/**
 *
 * 播放器，负责处理音频播放（下发至终端设备）。
 * 其生命周期大致与ChatSession相当，当没有播放音乐或绘本之类的时候，播放器不用切换。
 * 收到Abort事件时，才是需要主动停止播放的场景。其它时候应该都是自然停止。
 * 需要打断时从ChatSession 找到这个播放器用来打断并清理队列中的资源。
 * 当say goodbye 或工具调用发送友好提示时，也需要 插入播放。
 * 后期可以考虑 通过Composite的模式支持更多 需要播放的音频格式类型。播放器应该是有与终端设备约定的格式的。
 * TODO 所以 后续重构方向应该是将这个播放器做成 针对不同的格式的 播放器。
 *
 * setCloseAfterChat，只来源于两处，
 * @see com.xiaozhi.dialogue.llm.tool.function.SessionExitFunction
 * @see Persona#sendGoodbyeMessage()
 * 在SessionExitFunction工作时，这个工具是找不到Player的，即使在ChatSession里也可能是没有被初始化的Player实例的。
 * SessionExitFunction 正常返回一个GoodbyeMessage给到 DialogueService, 然后由DialogueService处理语音合成及播放。
 * sendGoodbyeMessage方法是被 checkInactiveSessions 所设用。
 *
 * @see com.xiaozhi.event.ChatAbortedEvent
 * 用户真正关心的是从说完话到开始播音的时间间隔。不是TTS的生成时间。所以Player需要有一个Instant。
 *
 * 问：是否需要实现Runnable接口？
 * 答：不是所有的Player实现类都需要实现Runnable，也可以通过ExecutorService / ScheduledExecutorService实现，可者聚合多个Player（Composite模式）。
 *
 */
@Slf4j
@Data
public abstract class Player {
    // 默认情况下，应当是false的。 随着向设备发送的消息而改变状态。
    private volatile boolean isPlaying = false;
    /**
     * 标记当前是否正在进行工具调用。
     * 工具调用期间（如拍照），
     * 需要等待工具返回后LLM继续输出。
     */
    private volatile boolean toolCalling = false;
    /**
     * 当前语音发送完毕后，执行的回调（如关闭session）
     */
    private Runnable functionAfterChat = null;
    protected final ChatSession session;
    protected final OpusProcessor opusProcessor = new OpusProcessor();
    private final MessageSender messageService;
    /**
     * 可选的 Opus 录制组件：将播放器发送的 Opus 帧同时写入 OGG 文件。
     * 通过组合模式替代原 PlayerWithOpusFile 的继承方式。
     */
    @Setter
    @Getter
    private OpusRecorder opusRecorder;

    /**
     * 音频播放器构造方法
     * @param session
     * @param messageService
     */
    protected Player(ChatSession session, MessageSender messageService) {
        Assert.notNull(session, "session不能为空");
        Assert.notNull(messageService, "messageService不能为空");
        this.session = session;
        this.messageService = messageService;
    }

    public void sendStt(String userText){
        messageService.sendSttMessage(session, userText);
    }

    /**
     * 发送TTS开始消息
     */
    protected void sendStart() {
        if (opusRecorder != null) {
            opusRecorder.onSendStart();
        }
        messageService.sendTtsMessage(session, null, "start");
        isPlaying = true;
        session.transitionTo(DeviceState.SPEAKING);
    }

    /**
     * 发送TTS句子开始消息
     */
    protected void sendSentenceStart( String text) {
        messageService.sendTtsMessage(session, text, "sentence_start");
    }

    /**
     * 发送Opus帧数据
     */
    protected void sendOpusFrame( byte[] opusFrame)  {
        messageService.sendBinaryMessage(session, opusFrame);
        // log.info("发送Opus帧数据: {}", opusFrame.length);
        if (opusRecorder != null) {
            opusRecorder.onSendOpusFrame(opusFrame);
        }
    }

    /**
     * 发送表情信息。如果句子里没有分析出表情，则默认返回 happy
     */
    protected void sendEmotion( String emotion) {
        messageService.sendEmotion(session, emotion);
    }

    /**
     * 发送停止消息
     * 此方法不对外暴露，只有播放器能发起停止消息。外部应该通过stop 或其它间接方式停止。
     */
    protected void sendStop() {
        try {
            if (opusRecorder != null) {
                opusRecorder.onSendStop();
            }
            messageService.sendTtsMessage(session, null, "stop");
            isPlaying = false;
            // tts stop 下发后设备切换到聆听状态，服务端同步为 LISTENING
            session.transitionTo(DeviceState.LISTENING);
            // 检查是否需要执行后续操作（如关闭会话）
            if (functionAfterChat != null) {
                functionAfterChat.run();
            }
        } catch (Exception e) {
            // sendStop 有可能是由于连接断掉而触发的，所以只打印异常，不再往外抛。
            log.error("发送停止消息失败", e);
        }
    }

    abstract public void play(Flux<Speech> speechFlux);

    public void play(Path audioPath) {
        play("",audioPath);
    }

    public void play(String text, Path audioPath) {

        File audioFile = audioPath.toFile();
        if (!audioFile.exists()) {
            log.error("音频文件不存在: {}", audioPath);
            return;
        }
        // 分块读取PCM，避免全量加载进内存
        try {
            List<byte[]> chunks = AudioUtils.readAsPcmChunks(audioPath.toString());
            AtomicBoolean first = new AtomicBoolean(true);
            play(Flux.fromIterable(chunks)
                    .map(chunk -> first.compareAndSet(true, false) ? new Speech(chunk, text) : new Speech(chunk)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 检查播放器是否有内容正在播放或待播放。
     * 基类默认实现等同于isPlaying()，子类可覆盖以包含队列等状态判断。
     * 用于打断判断，比isPlaying()更全面。
     */
    public boolean hasContent() {
        return isPlaying;
    }

    /**
     * 用于中断或用户打断时，清理资源。
     * 但这个对象是否需要被销毁取决于是否需要更换播放器。
     * 自然说完的时候，内部会控制sendStop，但内部不能调用这个stop方法。
     */
    public void stop() {
        // 子类（如ScheduledPlayer）会覆盖此方法进行更详细的清理
        log.info("已取消音频发送任务 - SessionId: {}", session.getSessionId());
    }

}

package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.OpusProcessor;
import io.jsonwebtoken.lang.Assert;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;


/**
 *
 * 播放器，负责处理音频播放（下发至终端设备）。
 * 其生命周期大致与ChatSession相当，当没有播放音乐或绘本之类的时候，播放器不用切换。
 * 收到Abort事件时，才是需要主动停止播放的场景。其它时候应该都是自然停止。
 * 需要打断时从ChatSession 找到这个播放器用来打断并清理队列中的资源。
 * 当say goodbye 或工具调用发送友好提示时，也需要 插入播放。
 *
 * @see com.xiaozhi.dialogue.llm.tool.function.SessionExitFunction
 * @see Persona#sendGoodbyeMessage()
 *
 * @see com.xiaozhi.event.ChatAbortEvent
 */
@Data
public abstract class Player{
    private static final Logger logger = LoggerFactory.getLogger(Player.class);

    // 默认情况下，应当是false的。 随着向设备发送的消息而改变状态。
    private volatile boolean isPlaying = false;
    /**
     * 当前语音发送完毕后，执行的回调（如关闭session）
     */
    private Runnable functionAfterChat = null;
    protected final ChatSession session;
    protected final OpusProcessor opusProcessor = new OpusProcessor();
    private final MessageService messageService;

    /**
     * 音频播放器构造方法
     * @param session
     * @param messageService
     */
    protected Player(ChatSession session, MessageService messageService) {
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
        messageService.sendTtsMessage(session, null, "start");
        isPlaying = true;
        session.setPlaying(true);
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
        // logger.info("发送Opus帧数据: {}", opusFrame.length);
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
            // 清除唤醒响应标记,允许恢复VAD检测
            if (session.isInWakeupResponse()) {
                session.setInWakeupResponse(false);
            }

            messageService.sendTtsMessage(session, null, "stop");
            isPlaying = false;
            session.setPlaying(false);
            // 检查是否需要执行后续操作（如关闭会话）
            if (functionAfterChat != null) {
                functionAfterChat.run();
            }
        } catch (Exception e) {
            // sendStop 有可能是由于连接断掉而触发的，所以只打印异常，不再往外抛。
            logger.error("发送停止消息失败", e);
        }
    }

    abstract public void play(Flux<Speech> speechFlux);


    public void play(Path audioPath) {
        play("",audioPath);
    }

    public void play(String text, Path audioPath) {

        File audioFile = audioPath.toFile();
        if (!audioFile.exists()) {
            logger.error("音频文件不存在: {}", audioPath);
            return;
        }
        // 处理音频文件
        try {
            byte[] audioData = AudioUtils.readAsPcm(audioPath.toString());
            play(Flux.just(new Speech(audioData, text)));
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
        logger.info("已取消音频发送任务 - SessionId: {}", session.getSessionId());
    }

}

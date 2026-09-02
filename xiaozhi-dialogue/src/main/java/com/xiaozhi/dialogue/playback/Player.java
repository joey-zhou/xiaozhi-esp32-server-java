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
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
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
    /**
     * 每次发出 tts stop 都会触发的回调，Persona 据此判定本轮回复已完整播出
     */
    private volatile Runnable onPlaybackStopped;
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
     * 本轮已开始下发的回复句子（发过 sentence_start 且来源是 LLM 回复的）。
     * 打断时据此把历史截到用户听到的位置：正在下发的那句算听到。
     * 问候语、推送、工具友好提示也会发 sentence_start，但不计入。
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final List<String> spokenSentences = new CopyOnWriteArrayList<>();
    /**
     * 最近下发过的所有句子（含问候语、推送、工具提示）及下发时刻，用于识别设备拾回自己声音形成的回声
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Deque<RecentSentence> recentSentences = new ConcurrentLinkedDeque<>();
    private static final long RECENT_SENTENCE_WINDOW_MS = 10_000;
    private static final int RECENT_SENTENCE_LIMIT = 20;

    private record RecentSentence(String normalized, long sentAtMillis) {}

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
    /**
     * @param reply 该句是否本轮 LLM 回复，只有回复句子计入打断截断
     */
    protected void sendSentenceStart(String text, boolean reply) {
        if (reply) {
            spokenSentences.add(text);
        }
        recentSentences.addLast(new RecentSentence(normalize(text), System.currentTimeMillis()));
        while (recentSentences.size() > RECENT_SENTENCE_LIMIT) {
            recentSentences.pollFirst();
        }
        messageService.sendTtsMessage(session, text, "sentence_start");
    }

    /**
     * 识别文本是否就是设备拾回的自己的声音。
     * 最近 10 秒内下发过的句子：去标点空白后相等，或不少于 5 字且被包含。
     * 正在播放时再放宽：最近 6 秒内下发的句子，识别文本至少 3 字且几乎全部按顺序出现在句中
     */
    public boolean recentlySpoke(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long since = now - RECENT_SENTENCE_WINDOW_MS;
        long playingSince = now - PLAYING_SENTENCE_WINDOW_MS;
        int length = normalized.codePointCount(0, normalized.length());
        for (RecentSentence recent : recentSentences) {
            if (recent.sentAtMillis() < since) {
                continue;
            }
            if (recent.normalized().equals(normalized)
                    || (length >= 5 && recent.normalized().contains(normalized))) {
                return true;
            }
            if (isPlaying && recent.sentAtMillis() >= playingSince && length >= 3
                    && subsequenceLength(normalized, recent.normalized()) * 1.0 / length > FUZZY_ECHO_RATIO) {
                return true;
            }
        }
        return false;
    }

    private static final long PLAYING_SENTENCE_WINDOW_MS = 6_000;
    private static final double FUZZY_ECHO_RATIO = 0.8;

    /** 语气词的同音写法折成一个字，识别结果与合成文本用字常不一致 */
    private static final Map<Character, Character> INTERJECTION_VARIANTS = Map.ofEntries(
            Map.entry('欸', '哎'), Map.entry('诶', '哎'), Map.entry('唉', '哎'), Map.entry('嗳', '哎'), Map.entry('嘿', '哎'),
            Map.entry('唔', '嗯'), Map.entry('恩', '嗯'),
            Map.entry('噢', '哦'), Map.entry('喔', '哦'),
            Map.entry('呀', '啊'), Map.entry('呐', '啊'));

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String stripped = text.replaceAll("[\\p{P}\\p{S}\\s]", "").toLowerCase();
        StringBuilder folded = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            folded.append(INTERJECTION_VARIANTS.getOrDefault(c, c));
        }
        return folded.toString();
    }

    /** 最长公共子序列长度 */
    static int subsequenceLength(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = a.charAt(i - 1) == b.charAt(j - 1) ? prev[j - 1] + 1 : Math.max(prev[j], cur[j - 1]);
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[b.length()];
    }

    /**
     * 新一轮回复开始前清空
     */
    public void resetSpokenSentences() {
        spokenSentences.clear();
    }

    /**
     * 本轮已开始下发的句子，按下发顺序
     */
    public List<String> spokenSentences() {
        return List.copyOf(spokenSentences);
    }

    /**
     * 本轮已开始下发的句子拼成的文本，没有则为空串
     */
    public String spokenText() {
        return String.join("", spokenSentences);
    }

    /**
     * 发送Opus帧数据
     */
    protected void sendOpusFrame( byte[] opusFrame)  {
        // 毫秒级时间戳（取低 32 位），随帧头下发；设备播放后在上行帧回显，用于 AEC 参考对齐
        long timestamp = System.currentTimeMillis() & 0xFFFFFFFFL;
        messageService.sendBinaryMessage(session, opusFrame, timestamp);
        // log.info("发送Opus帧数据: {}", opusFrame.length);
        if (opusRecorder != null) {
            opusRecorder.onSendOpusFrame(opusFrame, timestamp);
        }
    }

    /**
     * 下发一帧静音，保持设备播放时间轴连续。只作为 AEC 参考，不计入录音，不触发首帧回调
     */
    protected void sendSilenceFrame() {
        long timestamp = System.currentTimeMillis() & 0xFFFFFFFFL;
        byte[] frame = OpusProcessor.silenceFrame();
        messageService.sendBinaryMessage(session, frame, timestamp);
        if (opusRecorder != null) {
            opusRecorder.onSendSilenceFrame(frame, timestamp);
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
            Runnable stopped = onPlaybackStopped;
            if (stopped != null) {
                stopped.run();
            }
            // 检查是否需要执行后续操作（如关闭会话）
            if (functionAfterChat != null) {
                functionAfterChat.run();
            }
        } catch (Exception e) {
            // sendStop 有可能是由于连接断掉而触发的，所以只打印异常，不再往外抛。
            log.error("发送停止消息失败", e);
        }
    }

    /**
     * 播放非回复音频（问候语、推送、工具提示、音乐等）
     */
    public void play(Flux<Speech> speechFlux) {
        play(speechFlux, false);
    }

    /**
     * @param reply 是否本轮 LLM 回复，只有回复句子才计入打断截断
     */
    abstract public void play(Flux<Speech> speechFlux, boolean reply);

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
     * 待播内容是否已全部下发：为 true 且合成器已空闲，说明用户听到了完整回复
     */
    public boolean isDrained() {
        return true;
    }

    /**
     * 暂停下发，尚未开始的播放也不会开始；超过 maxMillis 未 resume 则自动恢复
     */
    public void pause(long maxMillis) {
    }

    public void resume() {
    }

    public boolean isPaused() {
        return false;
    }

    /**
     * 用于中断或用户打断时，清理资源。
     * 但这个对象是否需要被销毁取决于是否需要更换播放器。
     * 自然说完的时候，内部会控制sendStop，但内部不能调用这个stop方法。
     */
    public void stop() {
        isPlaying = false;
        // 子类（如ScheduledPlayer）会覆盖此方法进行更详细的清理
        log.info("已取消音频发送任务 - SessionId: {}", session.getSessionId());
    }

}

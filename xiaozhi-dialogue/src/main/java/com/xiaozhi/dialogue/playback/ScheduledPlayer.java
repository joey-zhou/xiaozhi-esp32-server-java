package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.Speech;
import com.xiaozhi.utils.EmojiUtils;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.utils.AudioUtils;
import io.jsonwebtoken.lang.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
/**
 * 基于虚拟线程的音频流播放器。
 *
 * 核心特性：
 * 1. 虚拟线程：每个播放器独立虚拟线程，支持无限并发
 * 2. Burst模式：前3帧预缓冲（-180ms），避免首帧破音/丢字，设备端整轮保持三帧队列
 * 3. 精确调度：纳秒级时间控制，保证60ms精确间隔
 * 4. 绝对时间：基于startTimestamp的绝对时间调度，避免累积误差
 * 5. 连续时间轴：开播后每个节拍都有帧下发，句间、暂停、上游断流、工具调用等待期间发静音帧，
 *    设备播放队列整轮不排空，服务端 AEC 参考与设备播放的对齐不随句子重建
 *
 * Burst模式原理：
 * - playPosition初始为-180ms（3帧）
 * - 前3帧立即发送（targetSendTime < currentTime，直接通过）
 * - 第4帧开始按精确时间调度
 * - 效果：设备收到前3帧立即开始播放，不会因等待数据而破音；服务端 AEC 参考保留两帧积压后仍领先播放点
 */
@Slf4j
public class ScheduledPlayer extends Player {
    // Opus帧发送间隔：60ms = 60,000,000 纳秒
    private static final long OPUS_FRAME_SEND_INTERVAL_NS = AudioUtils.OPUS_FRAME_DURATION_MS * 1_000_000L;

    // Burst模式：前3帧预缓冲，避免首帧破音
    private static final long BURST_PREBUFFER_NS = -OPUS_FRAME_SEND_INTERVAL_NS * 3; // -180ms

    // 等待设备把预缓冲的三帧播完再发送TTS结束消息
    private static final long WAIT_TIME_MS_TO_SEND_STOP = 180;

    // 句间静音帧数：句与句之间按节拍下发这么多帧静音，避免句子粘连
    private static final int SENTENCE_GAP_FRAMES = 4;

    // 句子间隔标记（空帧），发送线程遇到时转为句间静音帧
    private static final Frame SENTENCE_GAP_MARKER = new Frame(new Speech(new byte[0]), false);

    /** 队列里的一帧，带来源标记：是否本轮 LLM 回复 */
    private record Frame(Speech speech, boolean reply) {}

    /** 排队等待订阅的音频流，带来源标记 */
    private record QueuedFlux(Flux<Speech> flux, boolean reply) {}

    // 发送线程停顿超过此值视为失步，以当前时刻重锚定时间轴
    private static final long MAX_PLAYBACK_LAG_NS = 500 * 1_000_000L; // 500ms

    // Burst模式状态
    private long startTimestamp = 0;  // 播放开始的绝对时间戳（纳秒）
    private long playPosition = BURST_PREBUFFER_NS;  // 当前播放位置（纳秒），初始为-180ms实现预缓冲

    // 音频帧队列。暂停期间发送线程会把已取出的帧退回队头
    private final Deque<Frame> allOpusFrames = new ConcurrentLinkedDeque<>();

    // Flux队列（用于排队多个TTS任务）
    private final Queue<QueuedFlux> fluxQueue = new ConcurrentLinkedQueue<>();

    // 当前正在订阅的Flux
    private final AtomicReference<Disposable> fluxDisposable = new AtomicReference<>(null);

    // 虚拟线程控制
    private volatile boolean running = false;
    private Thread senderThread;

    // 待下发的句间静音帧数，修改须持有 pauseLock
    private volatile int gapFramesRemaining = 0;

    // 暂停下发：用户开口后先停住，等识别终稿决定续播还是真打断。队列、时间轴、订阅都保留。
    // 开播前暂停发送线程原地等待；开播后暂停按节拍发静音帧
    private final Object pauseLock = new Object();
    private volatile boolean paused = false;
    private long pauseDeadlineNs = 0;
    private long pauseStartNs = 0;

    // 播放代次。每次 stop()（打断/清理）递增，使此前订阅的 Flux 回调失效。
    // Player 是 session 级复用，打断后可能立即起新一轮对话；而上一轮的 TTS
    // WebSocket 回调线程可能慢一拍仍在往队列 add 残帧，若不隔离会串进新一轮播放。
    // subscribe() 捕获当轮代次，回调入队前校验代次未变，变了则丢弃残帧。
    private final AtomicInteger generation = new AtomicInteger(0);

    public ScheduledPlayer(ChatSession session, MessageSender messageService) {
        super(session, messageService);
    }

    /**
     * 播放音频流
     * @param speechFlux TTS生成的音频流
     * @param reply 是否本轮 LLM 回复
     */
    @Override
    public void play(Flux<Speech> speechFlux, boolean reply) {
        Assert.notNull(speechFlux, "speechFlux 不能为空");

        synchronized (fluxDisposable) {
            // 如果当前没有TTS在工作，直接订阅
            if (fluxDisposable.get() == null) {
                subscribe(speechFlux, reply);

                // 启动发送线程（只启动一次），tts start 由发送线程在首帧前发出
                if (!running) {
                    running = true;

                    // 使用虚拟线程，轻量级，可以创建成千上万个
                    senderThread = Thread.startVirtualThread(this::sendFramesLoop);
                }
            } else {
                // 当前已有TTS在工作，加入队列排队
                fluxQueue.offer(new QueuedFlux(speechFlux, reply));
            }
        }
    }

    /**
     * 订阅音频流
     */
    private void subscribe(Flux<Speech> speechFlux, boolean reply) {
        Assert.notNull(speechFlux, "speechFlux 不能为空");

        // 捕获当轮播放代次。若在本 Flux 存活期间发生过 stop()（打断），代次会递增，
        // 此后本订阅的所有回调都属于"已作废的上一轮"，必须丢弃，避免残帧串入新一轮。
        final int myGeneration = generation.get();

        // 当某句话的第一个PCM块太小、不足一个Opus帧时，文本暂存在此，等下一帧产生时再附加。
        // 使用局部变量而非类字段，每次subscribe()独立，subscribeNext()时自动重置，避免跨句污染。
        AtomicReference<String> pendingText = new AtomicReference<>(null);

        // 使用 boundedElastic 而非 single()
        // single() 是全局唯一线程，多个Player并发时会相互串行阻塞
        // boundedElastic 为每个订阅提供独立的弹性线程，适合TTS等含I/O阻塞的场景
        Disposable disposable = speechFlux.subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    speech -> {
                        // 代次已变（本轮已被 stop 打断）：丢弃残帧，不再入队
                        if (myGeneration != generation.get()) {
                            return;
                        }
                        // 更新活跃时间
                        session.setLastActivityTime(Instant.now());

                        // 预编码的 Opus 帧（来自缓存直读），直接入队无需转换
                        if (speech.isOpusEncoded()) {
                            allOpusFrames.add(new Frame(speech, reply));
                            return;
                        }

                        // 将PCM数据转换为Opus格式
                        byte[] pcmData = speech.getOutput();
                        String text = speech.getText();

                        // 句子边界对齐：带文本表示新句开始。先把上一句残留在编码器里的
                        // 不足一帧的 PCM flush 成独立帧，避免上一句尾音与本句首帧 PCM 拼接，
                        // 导致本句文本被绑定到混有上一句尾音的帧上（字幕相对音频提前、末句字幕丢失）。
                        if (StringUtils.hasText(text)) {
                            List<byte[]> tailFrames = opusProcessor.flushLeftover();
                            if (!CollectionUtils.isEmpty(tailFrames)) {
                                // 上一句的收尾帧不带文本，归属上一句
                                String carriedText = pendingText.getAndSet(null);
                                List<Speech> tailList = tailFrames.stream()
                                        .map(Speech::new)
                                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
                                // 若上一句因首帧 PCM 过小而暂存了文本却一直没凑够帧，
                                // 此刻补绑到其收尾帧，避免上一句字幕彻底丢失
                                if (StringUtils.hasText(carriedText)) {
                                    Speech firstTail = tailList.remove(0);
                                    tailList.add(0, new Speech(firstTail.getOutput(), carriedText));
                                }
                                allOpusFrames.addAll(frames(tailList, reply));
                            }
                        }

                        // 当前帧无文本，尝试取上次因PCM不足一帧而未能附加的文本
                        if (!StringUtils.hasText(text)) {
                            text = pendingText.getAndSet(null);
                        }

                        List<byte[]> opusFrames = opusProcessor.pcmToOpus(pcmData, true);

                        if (!CollectionUtils.isEmpty(opusFrames)) {
                            // 创建Speech列表，第一帧附带文本
                            List<Speech> speechList = opusFrames.stream()
                                    .map(Speech::new)
                                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

                            if (StringUtils.hasText(text)) {
                                // 将第一帧替换为带文本的Speech
                                Speech firstSpeech = speechList.remove(0);
                                speechList.add(0, new Speech(firstSpeech.getOutput(), text));
                                pendingText.set(null);
                            }

                            allOpusFrames.addAll(frames(speechList, reply));
                        } else if (StringUtils.hasText(text)) {
                            // PCM不足一个Opus帧（已进入编码器内部缓冲），暂存文本等待下一帧
                            pendingText.set(text);
                        }
                    },
                    throwable -> {
                        log.error("TTS模型生成输出内容时发生错误：{}", throwable.getMessage());
                        // 代次已变：本轮已作废，不再推进队列
                        if (myGeneration != generation.get()) {
                            return;
                        }
                        // 当前TTS抛出异常，尝试订阅下一个Flux
                        subscribeNext();
                    },
                    () -> {
                        // 代次已变（本轮已被 stop 打断）：丢弃收尾数据，也不订阅下一个 Flux
                        if (myGeneration != generation.get()) {
                            return;
                        }
                        // 当前Flux完成，flush剩余数据
                        List<byte[]> opusFrames = opusProcessor.flushLeftover();
                        if (!CollectionUtils.isEmpty(opusFrames)) {
                            List<Speech> speechList = opusFrames.stream()
                                    .map(Speech::new)
                                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

                            // 若有暂存文本（最后一句的第一帧太小），附加到flush出来的第一帧
                            String pt = pendingText.getAndSet(null);
                            if (pt != null) {
                                Speech firstSpeech = speechList.remove(0);
                                speechList.add(0, new Speech(firstSpeech.getOutput(), pt));
                            }

                            allOpusFrames.addAll(frames(speechList, reply));
                        }

                        // 添加句子间隔标记，避免句子粘连
                        allOpusFrames.add(SENTENCE_GAP_MARKER);

                        // 尝试订阅下一个Flux
                        subscribeNext();
                    }
                );

        fluxDisposable.set(disposable);
    }

    private static List<Frame> frames(List<Speech> speeches, boolean reply) {
        List<Frame> frames = new ArrayList<>(speeches.size());
        for (Speech speech : speeches) {
            frames.add(new Frame(speech, reply));
        }
        return frames;
    }

    /**
     * 订阅队列中的下一个Flux
     */
    private void subscribeNext() {
        synchronized (fluxDisposable) {
            QueuedFlux next = fluxQueue.poll();
            if (next != null) {
                subscribe(next.flux(), next.reply());
            } else {
                fluxDisposable.set(null);
            }
        }
    }

    /**
     * 音频帧发送循环（虚拟线程）
     *
     * 采用Burst模式 + 绝对时间调度：
     * 1. 第一帧时设置startTimestamp
     * 2. 根据playPosition计算目标发送时间
     * 3. playPosition初始为-180ms，前3帧立即发送（预缓冲）
     * 4. 后续帧精确按60ms间隔发送
     * 5. 开播后每个节拍都有帧下发：没有真帧的节拍发静音帧
     */
    private void sendFramesLoop() {
        // stop() 递增代次后中断本线程，本线程按代次退出，不改 running
        final int myGeneration = generation.get();
        try {
            runSendLoop(myGeneration);
        } catch (RuntimeException e) {
            // 发送失败（连接已断等）：结束本轮，不让 running 卡在 true
            log.error("音频发送线程异常退出 - SessionId: {}: {}", session.getSessionId(), e.getMessage());
            if (generation.get() == myGeneration) {
                running = false;
                setPlaying(false);
                startTimestamp = 0;
                playPosition = BURST_PREBUFFER_NS;
            }
        }
    }

    private void runSendLoop(int myGeneration) {
        while (running && generation.get() == myGeneration) {
            if (paused) {
                if (!isPlaying()) {
                    // 开播前暂停：原地等，不发 tts start
                    if (!awaitResume(myGeneration)) {
                        break;
                    }
                    continue;
                }
                if (resumeIfExpired()) {
                    continue;
                }
                // 开播后暂停：按节拍发静音，设备播放队列不排空
                if (!sendSilenceTick()) {
                    break;
                }
                continue;
            }

            Frame frame = allOpusFrames.peek();
            if (frame == SENTENCE_GAP_MARKER) {
                allOpusFrames.poll();
                synchronized (pauseLock) {
                    gapFramesRemaining += SENTENCE_GAP_FRAMES;
                }
                continue;
            }
            if (gapFramesRemaining > 0) {
                // 末句之后的间隔不播，直接收尾
                if (nothingMoreToPlay()) {
                    synchronized (pauseLock) {
                        gapFramesRemaining = 0;
                    }
                    continue;
                }
                if (!sendSilenceTick()) {
                    break;
                }
                synchronized (pauseLock) {
                    if (gapFramesRemaining > 0) {
                        gapFramesRemaining--;
                    }
                }
                continue;
            }
            if (frame != null) {
                allOpusFrames.poll();
                sendSpeechWithBurstMode(frame, myGeneration);
                continue;
            }

            // 队列为空
            if (fluxDisposable.get() == null && !isToolCalling()) {
                // 没有新的Flux在生成数据，准备结束
                try {
                    Thread.sleep(WAIT_TIME_MS_TO_SEND_STOP);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                // 再次检查，确保没有新数据
                if (nothingMoreToPlay()) {
                    running = false;
                    // 重置Burst模式状态，避免下次play()时因旧的startTimestamp导致所有帧以零延迟发送
                    startTimestamp = 0;
                    playPosition = BURST_PREBUFFER_NS;
                    sendStop();
                    break;
                }
                continue;
            }
            if (isPlaying()) {
                // 上游断流或工具调用等待：按节拍补静音
                if (!sendSilenceTick()) {
                    break;
                }
            } else {
                // 还未开播且还有Flux在生成数据，短暂休眠等待
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private boolean nothingMoreToPlay() {
        return allOpusFrames.isEmpty() && fluxDisposable.get() == null && !isToolCalling();
    }

    /**
     * 开播前暂停在此阻塞；超过期限自动恢复。返回 false 表示线程被中断，应退出循环
     */
    private boolean awaitResume(int myGeneration) {
        synchronized (pauseLock) {
            while (paused && running && generation.get() == myGeneration) {
                long remainingNs = pauseDeadlineNs - System.nanoTime();
                if (remainingNs <= 0) {
                    log.info("暂停超时，自动续播 - SessionId: {}", session.getSessionId());
                    doResume();
                    break;
                }
                try {
                    pauseLock.wait(Math.max(1, remainingNs / 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return running && generation.get() == myGeneration;
    }

    /**
     * 暂停超过期限则自动续播
     */
    private boolean resumeIfExpired() {
        synchronized (pauseLock) {
            if (!paused || System.nanoTime() < pauseDeadlineNs) {
                return false;
            }
            log.info("暂停超时，自动续播 - SessionId: {}", session.getSessionId());
            doResume();
            return true;
        }
    }

    @Override
    public void pause(long maxMillis) {
        synchronized (pauseLock) {
            if (!paused) {
                pauseStartNs = System.nanoTime();
            }
            paused = true;
            pauseDeadlineNs = System.nanoTime() + maxMillis * 1_000_000L;
        }
    }

    @Override
    public void resume() {
        synchronized (pauseLock) {
            if (paused) {
                doResume();
            }
        }
    }

    /** 调用方须持有 pauseLock。续播后立即接上，不再补句间静音 */
    private void doResume() {
        paused = false;
        log.info("续播，已暂停 {}ms - SessionId: {}", (System.nanoTime() - pauseStartNs) / 1_000_000L,
                session.getSessionId());
        gapFramesRemaining = 0;
        while (allOpusFrames.peek() == SENTENCE_GAP_MARKER) {
            allOpusFrames.poll();
        }
        pauseLock.notifyAll();
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    /**
     * 等到当前播放位置对应的发送时刻。返回 false 表示线程被中断
     */
    private boolean waitForSlot() {
        long currentTime = System.nanoTime();
        long delay = startTimestamp + playPosition - currentTime;

        if (delay < -MAX_PLAYBACK_LAG_NS) {
            log.info("发送线程失步，落后{}ms，重锚定时间轴 - SessionId: {}",
                    -delay / 1_000_000L, session.getSessionId());
            startTimestamp = currentTime - playPosition;
            delay = 0;
        }

        if (delay > 0) {
            try {
                Thread.sleep(delay / 1_000_000L, (int) (delay % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /**
     * 按节拍下发一帧静音。返回 false 表示线程被中断
     */
    private boolean sendSilenceTick() {
        if (!waitForSlot()) {
            return false;
        }
        sendSilenceFrame();
        playPosition += OPUS_FRAME_SEND_INTERVAL_NS;
        return true;
    }

    /**
     * 使用Burst模式发送单个Speech
     *
     * Burst模式时序：
     * - 第1帧：playPosition = -180ms → 立即发送（预缓冲）
     * - 第2帧：playPosition = -120ms → 立即发送（预缓冲）
     * - 第3帧：playPosition = -60ms  → 立即发送（预缓冲）
     * - 第4帧：playPosition = 0ms    → 等待到startTimestamp后发送
     * - 第5帧：playPosition = 60ms   → 等待到startTimestamp+60ms后发送
     * - ...
     */
    private void sendSpeechWithBurstMode(Frame queued, int myGeneration) {
        Speech speech = queued.speech();

        // 首帧前发 tts start
        if (!isPlaying()) {
            sendStart();
        }

        // 设置开始时间戳（只在第一帧时）
        if (startTimestamp == 0) {
            startTimestamp = System.nanoTime();
        }

        if (!waitForSlot()) {
            return;
        }

        // 等待期间被暂停：帧退回队头，续播后再发。与 stop() 清队列互斥
        synchronized (pauseLock) {
            if (generation.get() != myGeneration) {
                return;
            }
            if (paused) {
                allOpusFrames.addFirst(queued);
                return;
            }
        }

        // 更新活跃时间
        session.setLastActivityTime(Instant.now());

        // 发送文本和表情（如果有），与首帧音频紧邻发送
        String text = speech.getText();
        if (StringUtils.hasText(text)) {
            String mood = speech.getMood();
            sendEmotion(StringUtils.hasText(mood) ? mood : EmojiUtils.getRandomEmotion());
            sendSentenceStart(text, queued.reply());
        }

        // 发送音频帧
        sendOpusFrame(speech.getOutput());

        // 更新播放位置（每帧增加60ms）
        playPosition += OPUS_FRAME_SEND_INTERVAL_NS;
    }

    /**
     * 停止播放
     */
    @Override
    public void stop() {
        super.stop();
        running = false;

        // 先递增代次：让此前订阅的 Flux 回调（可能仍在 TTS 回调线程上飞）立即失效，
        // 之后它们的 add/addAll 会被 subscribe() 内的代次校验拦截，不会再污染队列。
        // 必须在 clear() 之前递增，否则存在"clear 完成→慢回调 add 残帧→新一轮开始"的窗口。
        generation.incrementAndGet();

        // 中断发送线程
        if (senderThread != null) {
            senderThread.interrupt();
        }

        // 解除暂停并清空队列。与发送线程退帧回队头互斥
        synchronized (pauseLock) {
            paused = false;
            gapFramesRemaining = 0;
            fluxQueue.clear();
            allOpusFrames.clear();
            pauseLock.notifyAll();
        }

        // 取消Flux订阅
        Disposable disposable = fluxDisposable.getAndSet(null);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }

        // 重置Burst模式状态
        startTimestamp = 0;
        playPosition = BURST_PREBUFFER_NS;

        // 丢弃本轮未成帧的残留样本，不能拼进下一轮首帧
        opusProcessor.discardLeftover();

        // 中断时主动关闭文件，避免产生损坏的 Opus 文件
        if (getOpusRecorder() != null) {
            getOpusRecorder().closeOpusFile();
        }
    }

    /**
     * 检查播放器是否正在播放或有待播放的内容
     * 用于打断判断，避免在句子切换时漏掉打断
     *
     * @return true 如果正在播放、有队列数据、有Flux在生成、或有Flux等待播放
     */
    public boolean hasContent() {
        return isPlaying() || !fluxQueue.isEmpty() || !allOpusFrames.isEmpty() || fluxDisposable.get() != null;
    }

    @Override
    public boolean isDrained() {
        return fluxQueue.isEmpty() && allOpusFrames.isEmpty() && fluxDisposable.get() == null;
    }
}

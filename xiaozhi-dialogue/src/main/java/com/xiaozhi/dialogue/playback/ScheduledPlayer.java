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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
/**
 * 基于虚拟线程的音频流播放器。
 *
 * 核心特性：
 * 1. 虚拟线程：每个播放器独立虚拟线程，支持无限并发
 * 2. Burst模式：前2帧预缓冲（-120ms），避免首帧破音/丢字
 * 3. 精确调度：纳秒级时间控制，保证60ms精确间隔
 * 4. 绝对时间：基于startTimestamp的绝对时间调度，避免累积误差
 *
 * Burst模式原理：
 * - playPosition初始为-120ms（2帧）
 * - 前2帧立即发送（targetSendTime < currentTime，直接通过）
 * - 第3帧开始按精确时间调度
 * - 效果：设备收到前2帧立即开始播放，不会因等待数据而破音
 */
@Slf4j
public class ScheduledPlayer extends Player {
    // Opus帧发送间隔：60ms = 60,000,000 纳秒
    private static final long OPUS_FRAME_SEND_INTERVAL_NS = AudioUtils.OPUS_FRAME_DURATION_MS * 1_000_000L;

    // Burst模式：前2帧预缓冲，避免首帧破音
    private static final long BURST_PREBUFFER_NS = -OPUS_FRAME_SEND_INTERVAL_NS * 2; // -120ms

    // 等待所有音频在终端设备播放完成后再发送TTS结束消息
    private static final long WAIT_TIME_MS_TO_SEND_STOP = 120;

    // 句子间隔：补偿预缓冲(2帧) + 预缓冲后第一帧(1帧) + 最后一帧(1帧) + 句子间隔(1帧) = 5帧 = 300ms
    // 这样可以避免句子粘连，给设备足够的缓冲时间
    private static final long SENTENCE_GAP_NS = OPUS_FRAME_SEND_INTERVAL_NS * 5;

    // 句子间隔标记（空帧），发送线程遇到时跳过发送并增加playPosition间隔
    private static final Speech SENTENCE_GAP_MARKER = new Speech(new byte[0]);

    // Burst模式状态
    private long startTimestamp = 0;  // 播放开始的绝对时间戳（纳秒）
    private long playPosition = BURST_PREBUFFER_NS;  // 当前播放位置（纳秒），初始为-120ms实现预缓冲

    // 音频帧队列
    private Queue<Speech> allOpusFrames = new ConcurrentLinkedQueue<>();

    // Flux队列（用于排队多个TTS任务）
    private Queue<Flux<Speech>> fluxQueue = new ConcurrentLinkedQueue<>();

    // 当前正在订阅的Flux
    private AtomicReference<Disposable> fluxDisposable = new AtomicReference<>(null);

    // 虚拟线程控制
    private volatile boolean running = false;
    private Thread senderThread;

    public ScheduledPlayer(ChatSession session, MessageSender messageService) {
        super(session, messageService);
    }

    /**
     * 播放音频流
     * @param speechFlux TTS生成的音频流
     */
    public void play(Flux<Speech> speechFlux) {
        Assert.notNull(speechFlux, "speechFlux 不能为空");

        synchronized (fluxDisposable) {
            // 如果当前没有TTS在工作，直接订阅
            if (fluxDisposable.get() == null) {
                subscribe(speechFlux);

                // 启动发送线程（只启动一次）
                if (!running) {
                    running = true;
                    sendStart();

                    // 使用虚拟线程，轻量级，可以创建成千上万个
                    senderThread = Thread.startVirtualThread(this::sendFramesLoop);
                }
            } else {
                // 当前已有TTS在工作，加入队列排队
                fluxQueue.offer(speechFlux);
            }
        }
    }

    /**
     * 订阅音频流
     */
    private void subscribe(Flux<Speech> speechFlux) {
        Assert.notNull(speechFlux, "speechFlux 不能为空");

        // 当某句话的第一个PCM块太小、不足一个Opus帧时，文本暂存在此，等下一帧产生时再附加。
        // 使用局部变量而非类字段，每次subscribe()独立，subscribeNext()时自动重置，避免跨句污染。
        AtomicReference<String> pendingText = new AtomicReference<>(null);

        // 使用 boundedElastic 而非 single()
        // single() 是全局唯一线程，多个Player并发时会相互串行阻塞
        // boundedElastic 为每个订阅提供独立的弹性线程，适合TTS等含I/O阻塞的场景
        Disposable disposable = speechFlux.subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    speech -> {
                        // 更新活跃时间
                        session.setLastActivityTime(Instant.now());

                        // 预编码的 Opus 帧（来自缓存直读），直接入队无需转换
                        if (speech.isOpusEncoded()) {
                            allOpusFrames.add(speech);
                            return;
                        }

                        // 将PCM数据转换为Opus格式
                        byte[] pcmData = speech.getOutput();
                        String text = speech.getText();

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

                            allOpusFrames.addAll(speechList);
                        } else if (StringUtils.hasText(text)) {
                            // PCM不足一个Opus帧（已进入编码器内部缓冲），暂存文本等待下一帧
                            pendingText.set(text);
                        }
                    },
                    throwable -> {
                        log.error("TTS模型生成输出内容时发生错误：{}", throwable.getMessage());
                        // 当前TTS抛出异常，尝试订阅下一个Flux
                        subscribeNext();
                    },
                    () -> {
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

                            allOpusFrames.addAll(speechList);
                        }

                        // 添加句子间隔标记，避免句子粘连
                        allOpusFrames.add(SENTENCE_GAP_MARKER);

                        // 尝试订阅下一个Flux
                        subscribeNext();
                    }
                );

        fluxDisposable.set(disposable);
    }

    /**
     * 订阅队列中的下一个Flux
     */
    private void subscribeNext() {
        synchronized (fluxDisposable) {
            Flux<Speech> nextFlux = fluxQueue.poll();
            if (nextFlux != null) {
                subscribe(nextFlux);
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
     * 3. playPosition初始为-120ms，前2帧立即发送（预缓冲）
     * 4. 后续帧精确按60ms间隔发送
     */
    private void sendFramesLoop() {
        while (running) {
            Speech speech = allOpusFrames.poll();

            if (speech != null) {
                if (speech == SENTENCE_GAP_MARKER) {
                    // 句子间隔：推进playPosition，不发送音频
                    playPosition += SENTENCE_GAP_NS;
                    continue;
                }
                // 有数据，发送音频帧
                sendSpeechWithBurstMode(speech);
            } else {
                // 队列为空，检查是否播放结束
                if (fluxDisposable.get() == null && !isToolCalling()) {
                    // 没有新的Flux在生成数据，准备结束
                    try {
                        Thread.sleep(WAIT_TIME_MS_TO_SEND_STOP);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    // 再次检查，确保没有新数据
                    if (allOpusFrames.isEmpty() && fluxDisposable.get() == null && !isToolCalling()) {
                        running = false;
                        // 重置Burst模式状态，避免下次play()时因旧的startTimestamp导致所有帧以零延迟发送
                        startTimestamp = 0;
                        playPosition = BURST_PREBUFFER_NS;
                        sendStop();
                        break;
                    }
                } else {
                    // 还有Flux在生成数据，短暂休眠等待
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    /**
     * 使用Burst模式发送单个Speech
     *
     * Burst模式时序：
     * - 第1帧：playPosition = -120ms → 立即发送（预缓冲）
     * - 第2帧：playPosition = -60ms  → 立即发送（预缓冲）
     * - 第3帧：playPosition = 0ms    → 等待到startTimestamp后发送
     * - 第4帧：playPosition = 60ms   → 等待到startTimestamp+60ms后发送
     * - ...
     */
    private void sendSpeechWithBurstMode(Speech speech) {
        byte[] frame = speech.getOutput();

        // 更新活跃时间
        session.setLastActivityTime(Instant.now());

        // 发送文本和表情（如果有）
        String text = speech.getText();
        if (StringUtils.hasText(text)) {
            String mood = speech.getMood();
            sendEmotion(StringUtils.hasText(mood) ? mood : EmojiUtils.getRandomEmotion());
            sendSentenceStart(text);
        }

        // 检查播放状态
        if (!isPlaying()) {
            log.error("播放器状态异常：在非Playing状态下发送音频帧 - SessionId: {}", session.getSessionId());
            sendStart();
        }

        // 设置开始时间戳（只在第一帧时）
        if (startTimestamp == 0) {
            startTimestamp = System.nanoTime();
        }

        // 计算目标发送时间（绝对时间戳）
        // playPosition初始为-120ms，前2帧会立即通过（targetSendTime < currentTime）
        long targetSendTime = startTimestamp + playPosition;

        // 等待到目标时间
        long currentTime = System.nanoTime();
        long delay = targetSendTime - currentTime;

        if (delay > 0) {
            // 需要等待
            try {
                long delayMs = delay / 1_000_000L;
                int delayNs = (int) (delay % 1_000_000L);
                Thread.sleep(delayMs, delayNs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
                return;
            }
        }
        // else: delay <= 0，立即发送（预缓冲阶段）

        // 发送音频帧
        sendOpusFrame(frame);

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

        // 中断发送线程
        if (senderThread != null) {
            senderThread.interrupt();
        }

        // 清空队列
        fluxQueue.clear();
        allOpusFrames.clear();

        // 取消Flux订阅
        Disposable disposable = fluxDisposable.getAndSet(null);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }

        // 重置Burst模式状态
        startTimestamp = 0;
        playPosition = BURST_PREBUFFER_NS;

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
}

package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.service.VadService.VadStatus;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.dialogue.stt.factory.SttServiceFactory;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.event.ChatSessionCloseEvent;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysRoleService;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.EmojiUtils;
import com.xiaozhi.utils.EmojiUtils.EmoSentence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 对话处理服务
 * 负责处理语音识别和对话生成的业务逻辑
 */
@Service
public class DialogueService implements ApplicationListener<ChatSessionCloseEvent> {
    private static final Logger logger = LoggerFactory.getLogger(DialogueService.class);
    private static final DecimalFormat df = new DecimalFormat("0.00");
    private static final long TIMEOUT_MS = 5000;
    
    // 从配置文件读取TTS相关参数
    @Value("${tts.timeout.ms:10000}")
    private long TTS_TIMEOUT_MS;
    
    @Value("${tts.max.retry.count:1}")
    private int MAX_RETRY_COUNT;
    
    @Value("${tts.retry.delay.ms:1000}")
    private long TTS_RETRY_DELAY_MS;
    
    @Value("${tts.max.concurrent.per.session:3}")
    private int MAX_CONCURRENT_PER_SESSION;

    @Resource
    private ChatService chatService;

    @Resource
    private AudioService audioService;

    @Resource
    private TtsServiceFactory ttsFactory;

    @Resource
    private SttServiceFactory sttFactory;

    @Resource
    private MessageService messageService;

    @Resource
    private MusicService musicService;

    @Resource
    private HuiBenService huiBenService;

    @Resource
    private VadService vadService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private SysConfigService configService;
    
    @Resource
    private SysRoleService roleService;

    // 会话状态管理
    private final Map<String, AtomicInteger> seqCounters = new ConcurrentHashMap<>();
    private final Map<String, Long> sttStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> llmStartTimes = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Sentence>> sentenceQueue = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> firstSentDone = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    // 存储每个对话ID的所有模型回复音频路径
    private final Map<Long, Map<Integer, String>> dialogueAudioPaths = new ConcurrentHashMap<>();
    // 存储每个对话ID的完整文本回复
    private final Map<Long, StringBuilder> dialogueResponses = new ConcurrentHashMap<>();

    // 新增：并发控制相关
    private final Map<String, Semaphore> sessionSemaphores = new ConcurrentHashMap<>();
    private final Map<String, PriorityBlockingQueue<TtsTask>> sessionTaskQueues = new ConcurrentHashMap<>();

    @Override
    public void onApplicationEvent(ChatSessionCloseEvent event) {
        ChatSession chatSession = event.getSession();
        if(chatSession != null) {
            // clean up dialogue audio paths and responses
            Long assistantTimeMillis = chatSession.getAssistantTimeMillis();
            if (assistantTimeMillis!=null ) {
                dialogueAudioPaths.remove(assistantTimeMillis);
                dialogueResponses.remove(assistantTimeMillis);
            }
            cleanupSession(chatSession.getSessionId());
        }
    }

    /**
     * 句子对象，用于跟踪每个句子的处理状态
     */
    public static class Sentence {
        private int seq;
        private final String text;
        private boolean isFirst;
        private boolean isLast;
        private boolean ready = false;
        private String audioPath = null;
        private long timestamp = System.currentTimeMillis();
        private double modelResponseTime = 0.0; // 模型响应时间（秒）
        private double ttsGenerationTime = 0.0; // TTS生成时间（秒）
        private Long assistantTimeMillis = null; // 对话ID
        private List<String> moods;
        // TODO 看看是否真的需要这么多个构造方法。
        public Sentence(String text) {
            this.text = text;
        }

        public Sentence(String text, String audioPath) {
            this.text = text;
            this.audioPath = audioPath;
        }

        public Sentence(int seq, String text, boolean isFirst, boolean isLast) {
            this.seq = seq;
            this.text = text;
            this.isFirst = isFirst;
            this.isLast = isLast;
        }

        public void setAudio(String path) {
            this.audioPath = path;
            this.ready = true;
        }

        public boolean isReady() {
            return ready;
        }

        public boolean isTimeout() {
            return System.currentTimeMillis() - timestamp > TIMEOUT_MS;
        }

        public int getSeq() {
            return seq;
        }

        public String getText() {
            return text;
        }

        public boolean isFirst() {
            return isFirst;
        }

        public boolean isLast() {
            return isLast;
        }

        public String getAudioPath() {
            return audioPath;
        }

        public void setModelResponseTime(double time) {
            this.modelResponseTime = time;
        }

        public double getModelResponseTime() {
            return modelResponseTime;
        }

        public void setTtsGenerationTime(double time) {
            this.ttsGenerationTime = time;
        }

        public double getTtsGenerationTime() {
            return ttsGenerationTime;
        }

        public void setAssistantTimeMillis(Long assistantTimeMillis) {
            this.assistantTimeMillis = assistantTimeMillis;
        }

        public Long getAssistantTimeMillis() {
            return assistantTimeMillis;
        }

        public List<String> getMoods() {
            return moods;
        }

        public void setMoods(List<String> moods) {
            this.moods = moods;
        }
    }

    /**
     * TTS任务封装，用于优先队列
     */
    private static class TtsTask implements Comparable<TtsTask> {
        private final String sessionId;
        private final Sentence sentence;
        private final EmoSentence emoSentence;
        private final boolean isFirst;
        private final boolean isLast;
        private final SysConfig ttsConfig;
        private final String voiceName;
        private final ChatSession session;
        private final long createTime;
        private int retryCount = 0;
        private boolean isRetry = false;

        public TtsTask(ChatSession session, String sessionId, Sentence sentence,
                EmoSentence emoSentence, boolean isFirst, boolean isLast,
                SysConfig ttsConfig, String voiceName) {
            this.session = session;
            this.sessionId = sessionId;
            this.sentence = sentence;
            this.emoSentence = emoSentence;
            this.isFirst = isFirst;
            this.isLast = isLast;
            this.ttsConfig = ttsConfig;
            this.voiceName = voiceName;
            this.createTime = System.currentTimeMillis();
        }

        @Override
        public int compareTo(TtsTask other) {
            // 优先级：重试任务 > 首句 > 序号小的句子
            if (this.isRetry != other.isRetry) {
                return this.isRetry ? -1 : 1;
            }
            if (this.isFirst != other.isFirst) {
                return this.isFirst ? -1 : 1;
            }
            return Integer.compare(this.sentence.getSeq(), other.sentence.getSeq());
        }

        public String getSessionId() {
            return this.sessionId;
        }
    }

    /**
     * 获取或创建session的信号量
     */
    private Semaphore getSessionSemaphore(String sessionId) {
        return sessionSemaphores.computeIfAbsent(sessionId,
                k -> new Semaphore(MAX_CONCURRENT_PER_SESSION));
    }

    /**
     * 获取或创建session的任务队列
     */
    private PriorityBlockingQueue<TtsTask> getSessionTaskQueue(String sessionId) {
        return sessionTaskQueues.computeIfAbsent(sessionId,
                k -> new PriorityBlockingQueue<>());
    }

    /**
     * 处理音频数据
     */
    public void processAudioData(ChatSession session, byte[] opusData) {
            try {
                String sessionId = session.getSessionId();
                SysDevice device = session.getSysDevice();
                // 如果设备未注册或未绑定，忽略音频数据
                if (device == null || ObjectUtils.isEmpty(device.getRoleId())) {
                    return;
                }
                SysRole role = roleService.selectRoleById(device.getRoleId());
                // 获取STT和TTS配置
                SysConfig sttConfig = role.getSttId() != null ? configService.selectConfigById(role.getSttId())
                        : null;

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
                        // 检测到语音开始
                        sttStartTimes.put(sessionId, System.currentTimeMillis());

                        // 初始化对话状态
                        initChat(sessionId);
                        startStt(session, sessionId, sttConfig, device, vadResult.getProcessedData());
                        break;

                    case SPEECH_CONTINUE:
                        // 语音继续，发送数据到流式识别
                        if (sessionManager.isStreaming(sessionId)) {
                            sessionManager.sendAudioData(sessionId, vadResult.getProcessedData());
                        }
                        break;

                    case SPEECH_END:
                        // 语音结束，完成流式识别
                        if (sessionManager.isStreaming(sessionId)) {
                            sessionManager.completeAudioStream(sessionId);
                            sessionManager.setStreamingState(sessionId, false);
                        }
                        break;

                    default:
                        break;
                }
            } catch (Exception e) {
                logger.error("处理音频数据失败: {}", e.getMessage(), e);
            }
    }

    /**
     * 启动语音识别
     */
    private void startStt(
            ChatSession session,
            String sessionId,
            SysConfig sttConfig,
            SysDevice device,
            byte[] initialAudio) {
        Assert.notNull(session, "session不能为空");
        logger.info("startStt={}", sessionId);
        Thread.startVirtualThread(() -> {
            try {
                // 如果正在播放，先中断音频
                if (audioService.isPlaying(sessionId)) {
                    sentenceQueue.get(sessionId).clear();
                    audioService.sendStop(session);
                }

                // 如果已经在进行流式识别，先清理旧的资源
                sessionManager.closeAudioStream(sessionId);

                // 创建新的音频数据接收管道
                sessionManager.createAudioStream(sessionId);
                sessionManager.setStreamingState(sessionId, true);

                // 获取STT服务
                SttService sttService = sttFactory.getSttService(sttConfig);
                if (sttService == null) {
                    logger.error("无法获取STT服务 - Provider: {}", sttConfig != null ? sttConfig.getProvider() : "null");
                    return;
                }

                // 发送初始音频数据
                if (initialAudio != null && initialAudio.length > 0) {
                    sessionManager.sendAudioData(sessionId, initialAudio);
                }

                // 设置用户收到音频的时间戳作为用户消息的创建时间戳，也用于约定保存音频文件的路径。一定要在STT前获得时间戳。
                final Long userTimeMillis =  System.currentTimeMillis();
                session.setUserTimeMillis(userTimeMillis);

                final String finalText;
                if (sessionManager.getAudioStream(sessionId) != null) {
                    finalText = sttService.streamRecognition(sessionManager.getAudioStream(sessionId));
                    if (!StringUtils.hasText(finalText)) {
                        return;
                    }
                } else {
                    return;
                }
                // 获取完整的音频数据并保存
                saveUserAudio(session);

                CompletableFuture.runAsync(() -> messageService.sendSttMessage(session, finalText))
                        .thenRun(() -> audioService.sendStart(session))
                        .thenRun(() -> {
                            // 设置LLM生成消息的时间戳作为Assistant消息的创建时间戳，也用于约定保存音频文件的路径。一定要在LLM前设置时间戳。
                            final Long assistantTimeMillis =  System.currentTimeMillis();
                            session.setAssistantTimeMillis(assistantTimeMillis);
                            // 初始化当前对话的音频路径映射和文本响应
                            dialogueAudioPaths.put(assistantTimeMillis, new ConcurrentHashMap<>());
                            dialogueResponses.put(assistantTimeMillis, new StringBuilder());
                            // 使用句子切分处理响应
                            chatService.chatStreamBySentence(session, finalText, true,
                                    (sentence, isFirst, isLast) -> {
                                        handleSentence(
                                                session,
                                                sentence,
                                                isFirst,
                                                isLast);
                                    });
                        })
                        .exceptionally(e -> {
                            logger.error("处理对话失败: {}", e.getMessage(), e);
                            return null;
                        });
            } catch (Exception e) {
                logger.error("流式识别错误: {}", e.getMessage(), e);
            }
        });
        logger.info("end Stt={}", sessionId);
    }

    /**
     * 保存用户音频数据
     */
    private void saveUserAudio(ChatSession session) {
        try {
            // 获取当前语音活动的PCM数据
            List<byte[]> pcmFrames = vadService.getPcmData(session.getSessionId());

            if (pcmFrames != null && !pcmFrames.isEmpty()) {
                // 计算总大小并合并PCM帧
                int totalSize = pcmFrames.stream().mapToInt(frame -> frame.length).sum();
                byte[] fullPcmData = new byte[totalSize];
                int offset = 0;

                for (byte[] frame : pcmFrames) {
                    System.arraycopy(frame, 0, fullPcmData, offset, frame.length);
                    offset += frame.length;
                }

                // 保存为WAV文件
                Path path = session.getUserAudioPath();
                AudioUtils.saveAsWav(path,fullPcmData);

                logger.debug("用户音频已保存: {}", path.toString());
            }
        } catch (Exception e) {
            logger.error("保存用户音频失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化对话状态
     */
    private void initChat(String sessionId) {
        llmStartTimes.put(sessionId, System.currentTimeMillis());
        seqCounters.putIfAbsent(sessionId, new AtomicInteger(0));
        sentenceQueue.putIfAbsent(sessionId, new CopyOnWriteArrayList<>());
        firstSentDone.put(sessionId, new AtomicBoolean(false));
        locks.putIfAbsent(sessionId, new ReentrantLock());
    }

    /**
     * 处理LLM返回的句子
     * 使用虚拟线程处理TTS生成
     */
    private void handleSentence(
            ChatSession session,
            String text,
            boolean isFirst,
            boolean isLast) {
        Assert.notNull(session, "session cannot be null");
        Long assistantTimeMillis = session.getAssistantTimeMillis();
        Assert.notNull(assistantTimeMillis, "assistantTimeMillis cannot be null");
        String sessionId = session.getSessionId();
        seqCounters.putIfAbsent(sessionId, new AtomicInteger(0));
        // 获取句子序列号
        int seq = seqCounters.get(sessionId).incrementAndGet();
        
        // 耗时操作需及时更新最后活动时间，避免误判为会话终止
        sessionManager.updateLastActivity(sessionId);

        // 累加完整回复内容
        if (text != null && !text.isEmpty()) {
            // 同时累加到对话ID对应的响应中
            dialogueResponses.computeIfAbsent(assistantTimeMillis, k -> new StringBuilder()).append(text);
        }

        // 计算模型响应时间
        final double responseTime;
        Long startTime = llmStartTimes.get(sessionId);
        if (startTime != null) {
            responseTime = (System.currentTimeMillis() - startTime) / 1000.0;
        } else {
            responseTime = 0.0;
        }

        SysDevice device = session.getSysDevice();
        SysRole role = roleService.selectRoleById(device.getRoleId());
        if (device == null || role == null) {
            return;
        }

        // 新增加的设备很有可能没有配置TTS，采用默认Edge需要传递null
        final SysConfig ttsConfig;
        if (role.getTtsId() != null) {
            ttsConfig = configService.selectConfigById(role.getTtsId());
        } else {
            ttsConfig = null;
        }
        String voiceName = role.getVoiceName();

        // 处理表情符号
        EmoSentence emoSentence = EmojiUtils.processSentence(text);

        // 检查处理后的文本是否为空（即只有表情符号）
        if (emoSentence.getTtsSentence() == null || emoSentence.getTtsSentence().trim().isEmpty()) {
            // 如果只有表情符号，直接标记为准备好但不生成音频
            logger.info("跳过纯表情符号TTS处理 - 序号: {}, 内容: \"{}\"", seq, text);
            
            // 创建句子对象
            Sentence sentence = new Sentence(seq, text, isFirst, isLast);
            sentence.setModelResponseTime(responseTime / 1000.0);
            sentence.setAssistantTimeMillis(assistantTimeMillis);
            sentence.setAudio(null);
            sentence.setTtsGenerationTime(0);
            sentence.setMoods(emoSentence.getMoods());

            // 添加到句子队列
            CopyOnWriteArrayList<Sentence> queue = sentenceQueue.get(sessionId);
            if (queue != null) {
                queue.add(sentence);
            }

            // 如果是首句，需要标记首句处理完成
            if (isFirst) {
                firstSentDone.get(sessionId).set(true);
            }

            // 尝试处理队列
            processQueue(session, sessionId);
            return;
        }

        // 创建句子对象
        Sentence sentence = new Sentence(seq, text, isFirst, isLast);
        sentence.setModelResponseTime(responseTime / 1000.0); // 记录模型响应时间
        sentence.setAssistantTimeMillis(assistantTimeMillis); // 设置对话ID

        logger.info("处理LLM返回的句子: seq={}, text={}, isFirst={}, isLast={}, responseTime={}s", seq, text, isFirst, isLast, responseTime/1000);

        // 添加到句子队列
        CopyOnWriteArrayList<Sentence> queue = sentenceQueue.get(sessionId);
        if (queue == null) {return;}
        queue.add(sentence);

        // 如果句子为空且是结束状态，直接标记为准备好（不需要生成音频）
        if ((text == null || text.isEmpty()) && isLast) {
            sentence.setAudio(null);
            sentence.setTtsGenerationTime(0); // 设置TTS生成时间为0

            // 如果是首句，需要标记首句处理完成
            if (isFirst) {
                firstSentDone.get(sessionId).set(true);
            }

            // 尝试处理队列
            processQueue(session, sessionId);
            return;
        }

        // 使用虚拟线程异步生成音频文件
        Thread.startVirtualThread(() -> {
            generateAudio(session, sessionId, sentence, emoSentence, isFirst, isLast, ttsConfig, voiceName);
        });
    }

    /**
     * 生成音频并处理
     * TODO 考虑inline , 无实质内容。可以另外切分函数得更优雅。
     */
    private void generateAudio(
            ChatSession session,
            String sessionId,
            Sentence sentence,
            EmoSentence emoSentence,
            boolean isFirst,
            boolean isLast,
            SysConfig ttsConfig,
            String voiceName) {

        // 创建TTS任务
        TtsTask task = new TtsTask(session, sessionId, sentence, emoSentence,
                isFirst, isLast, ttsConfig, voiceName);

        // 提交任务到队列
        submitTtsTask(task);
    }

    /**
     * 提交TTS任务
     */
    private void submitTtsTask(TtsTask task) {
        PriorityBlockingQueue<TtsTask> taskQueue = getSessionTaskQueue(task.sessionId);
        taskQueue.offer(task);

        // 尝试处理队列中的任务
        processTtsTaskQueue(task.sessionId);
    }

    /**
     * 处理TTS任务队列
     */
    private void processTtsTaskQueue(String sessionId) {
        Thread.startVirtualThread(() -> {
            PriorityBlockingQueue<TtsTask> taskQueue = getSessionTaskQueue(sessionId);
            Semaphore semaphore = getSessionSemaphore(sessionId);

            while (!taskQueue.isEmpty()) {
                // 耗时操作需及时更新最后活动时间，避免误判为会话终止
                sessionManager.updateLastActivity(sessionId);

                // 尝试获取许可
                if (!semaphore.tryAcquire()) {
                    // 无法获取许可，等待其他任务完成
                    break;
                }

                TtsTask task = taskQueue.poll();
                if (task == null) {
                    semaphore.release();
                    break;
                }

                // 使用虚拟线程执行任务
                Thread.startVirtualThread(() -> {
                    try {
                        executeTtsTask(task);
                    } finally {
                        semaphore.release();
                        // 任务完成后，继续处理队列
                        processTtsTaskQueue(sessionId);
                    }
                });
            }
        });
    }

    /**
     * 执行TTS任务（带超时和重试）
     */
    private void executeTtsTask(TtsTask task) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                long ttsStartTime = System.currentTimeMillis();
                String audioPath = ttsFactory.getTtsService(task.ttsConfig, task.voiceName)
                        .textToSpeech(task.emoSentence.getTtsSentence());
                long ttsDuration = System.currentTimeMillis() - ttsStartTime;

                // 记录TTS生成时间
                task.sentence.setTtsGenerationTime(ttsDuration / 1000.0);
                return audioPath;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, Thread::startVirtualThread);

        try {
            // 耗时操作需及时更新最后活动时间，避免误判为会话终止
            sessionManager.updateLastActivity(task.getSessionId());

            // 设置超时
            String audioPath = future.get(TTS_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // 成功生成音频
            handleTtsSuccess(task, audioPath);
        } catch (TimeoutException e) {
            // logger.warn("TTS生成超时 - 序号: {}, 重试次数: {}/{}, 内容: \"{}\"",
            //         task.sentence.getSeq(), task.retryCount, MAX_RETRY_COUNT, task.sentence.getText());
            handleTtsFailure(task, "超时");
        } catch (Exception e) {
            // logger.error("TTS生成失败 - 序号: {}, 重试次数: {}/{}, 错误: {}",
            //         task.sentence.getSeq(), task.retryCount, MAX_RETRY_COUNT, e.getMessage());
            handleTtsFailure(task, e.getMessage());
        }
    }

    /**
     * 处理TTS成功
     */
    private void handleTtsSuccess(TtsTask task, String audioPath) {
        // 记录心情
        task.sentence.setMoods(task.emoSentence.getMoods());

        // 耗时操作需及时更新最后活动时间，避免误判为会话终止
        sessionManager.updateLastActivity(task.getSessionId());
        // 记录日志
        logger.info("句子音频生成完成 - 序号: {}, 对话ID: {}, 模型响应: {}秒, 语音生成: {}秒, 内容: \"{}\"",
                task.sentence.getSeq(), task.sentence.getAssistantTimeMillis(),
                df.format(task.sentence.getModelResponseTime()),
                df.format(task.sentence.getTtsGenerationTime()),
                task.sentence.getText());

        // 标记音频准备就绪
        task.sentence.setAudio(audioPath);

        // 如果有对话ID，将音频路径添加到对应的映射中
        if (task.sentence.getAssistantTimeMillis() != null && audioPath != null) {
            dialogueAudioPaths.computeIfAbsent(task.sentence.getAssistantTimeMillis(), k -> new ConcurrentHashMap<>())
                    .put(task.sentence.getSeq(), audioPath);
        }

        // 如果是首句，需要标记首句处理完成
        if (task.isFirst) {
            if(firstSentDone.get(task.sessionId) != null) {
                firstSentDone.get(task.sessionId).set(true);
            } else {
                logger.error("会话 {} 已经被删除，无法标记首句处理完成。", task.sessionId);
            }
        }

        // 尝试处理队列
        AtomicBoolean firstDone = firstSentDone.get(task.sessionId);
        if (firstDone != null && firstDone.get()) {
            processQueue(task.session, task.sessionId);
        }
    }

    /**
     * 处理TTS失败
     */
    private void handleTtsFailure(TtsTask task, String reason) {
        task.retryCount++;
    
        // 耗时操作需及时更新最后活动时间，避免服务端误判为会话终止
        sessionManager.updateLastActivity(task.getSessionId());
        // 异常或失败，发送类似心跳包，避免设备端误判为会话终止
        messageService.sendEmotion(task.session, "happy");

        if (task.retryCount <= MAX_RETRY_COUNT) {
            // 创建新的任务对象而不是重用原对象，避免数据污染
            TtsTask retryTask = new TtsTask(
                task.session, 
                task.sessionId, 
                task.sentence, 
                task.emoSentence, 
                task.isFirst, 
                task.isLast, 
                task.ttsConfig, 
                task.voiceName
            );
            retryTask.retryCount = task.retryCount;
            retryTask.isRetry = true;
            
            logger.info("TTS任务重试 - 序号: {}, 重试次数: {}/{}, 内容: \"{}\", 原因: {}",
                    task.sentence.getSeq(), task.retryCount, MAX_RETRY_COUNT, task.sentence.getText(), reason);

            // 延迟后重试
            CompletableFuture.delayedExecutor(TTS_RETRY_DELAY_MS * task.retryCount, TimeUnit.MILLISECONDS)
                    .execute(() -> submitTtsTask(retryTask));
        } else {
            // 超过最大重试次数，标记为失败
            logger.error("TTS任务失败 - 序号: {}, 重试次数: {}/{}, 已达最大重试次数, 原因: {}",
                    task.sentence.getSeq(), task.retryCount, MAX_RETRY_COUNT, reason);

            // 即使失败也标记为准备好，以便队列继续处理
            task.sentence.setAudio(null);
            task.sentence.setTtsGenerationTime(0);

            // 如果是首句，需要标记首句处理完成
            if (task.isFirst) {
                if(firstSentDone.get(task.sessionId) != null) {
                    firstSentDone.get(task.sessionId).set(true);
                } else {
                    logger.error("会话 {} 已经被删除，无法标记首句处理完成。", task.sessionId);
                }
            }

            // 尝试处理队列
            if(firstSentDone.get(task.sessionId) != null ) {
                if (firstSentDone.get(task.sessionId).get()) {
                    processQueue(task.session, task.sessionId);
                }
            } else {
                logger.error("会话 {} 已经被删除，无法处理队列。", task.sessionId);
            }
        }
    }

    /**
     * 保存助手的完整响应（文本和合并音频）
     */
    private void saveAssistantResponse(ChatSession session) {
        Long assistantTimeMillis = session.getAssistantTimeMillis();
        try {
            // 获取该对话的所有音频路径
            Map<Integer, String> audioPaths = dialogueAudioPaths.get(assistantTimeMillis);
            if (audioPaths == null || audioPaths.isEmpty()) {
                logger.warn("对话 {} 没有可用的音频路径", assistantTimeMillis);
                return;
            }

            // 按序号排序音频路径
            List<Integer> sortedSeqs = new ArrayList<>(audioPaths.keySet());
            sortedSeqs.sort(Integer::compareTo);

            // 准备要合并的音频文件路径
            List<String> audioFilesToMerge = new ArrayList<>();
            for (Integer seq : sortedSeqs) {
                String path = audioPaths.get(seq);
                if (path != null) {
                    audioFilesToMerge.add(path);
                }
            }

            // 合并音频文件
            if (!audioFilesToMerge.isEmpty()) {
                Path path = session.getAssistantAudioPath();
                AudioUtils.mergeAudioFiles(path,audioFilesToMerge);
                // 保存合并后的音频路径
                logger.info("对话 {} 的音频已合并: {}", assistantTimeMillis, path);
            }
        } catch (Exception e) {
            logger.error("保存助手响应失败 - 对话ID: {}, 错误: {}", assistantTimeMillis, e.getMessage(), e);
        }
    }

    /**
     * 处理音频队列
     * 在流式处理完成后或非首句音频生成完成后调用
     */
    private void processQueue(ChatSession session, String sessionId) {
        logger.info("processQueue sessionId={}", sessionId);
        // 获取锁，确保线程安全
        ReentrantLock lock = locks.get(sessionId);
        if (lock == null) {
            return;
        }

        // 尝试获取锁，避免多线程同时处理
        if (!lock.tryLock()) {
            return;
        }

        try {
            // 获取句子队列
            CopyOnWriteArrayList<Sentence> queue = sentenceQueue.get(sessionId);
            if (queue == null || queue.isEmpty()) {
                return;
            }

            // 检查首句是否已经流式处理完成
            AtomicBoolean firstDone = firstSentDone.get(sessionId);
            if (firstDone == null || !firstDone.get()) {
                // 首句尚未处理完成，等待
                return;
            }

            // 检查当前是否有句子正在播放
            boolean isCurrentlyPlaying = audioService.isPlaying(sessionId);
            logger.info("isCurrentlyPlaying={}", isCurrentlyPlaying);
            // 如果当前正在播放，不处理下一个句子
            if (isCurrentlyPlaying) {
                return;
            }

            // 找出最小序号
            int minSeq = queue.stream()
                    .mapToInt(Sentence::getSeq)
                    .min()
                    .orElse(Integer.MAX_VALUE);

            // 找出该序号的句子
            Sentence nextSentence = queue.stream()
                    .filter(s -> s.getSeq() == minSeq)
                    .findFirst()
                    .orElse(null);

            if (nextSentence != null) {
                // 检查句子是否准备好或超时
                if (nextSentence.isReady() || nextSentence.isTimeout()) {
                    // 如果句子超时但未准备好，标记为准备好但没有音频
                    if (nextSentence.isTimeout() && !nextSentence.isReady()) {
                        nextSentence.setAudio(null);
                    }

                    // 从队列中移除已处理的句子
                    queue.remove(nextSentence);

                    // 发送到客户端
                    audioService.sendAudioMessage(
                            session,
                            nextSentence,
                            false, // 不是开始消息
                            nextSentence.isLast() // 如果是最后一句，则是结束消息
                    ).thenRun(() -> {
                        // 在播放完成后，递归调用处理下一个句子
                        processQueue(session, sessionId);
                    });
                }
                //logger.info("是否最后一个句子{}, 对话ID: {}",nextSentence.isLast, nextSentence.assistantTimeMillis);
                // 如果是最后一个句子，合并并存储助手的完整音频
                if (nextSentence.isLast() && nextSentence.getAssistantTimeMillis() != null) {
                    saveAssistantResponse(session);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 处理语音唤醒
     */
    public void handleWakeWord(ChatSession session, String text) {
        logger.info("检测到唤醒词: \"{}\"", text);
        try {
            String sessionId = session.getSessionId();
            SysDevice device = sessionManager.getDeviceConfig(sessionId);
            if (device == null) {
                return;
            }

            handleText(session, text, timeMillis -> {
                // 使用句子切分处理流式响应
                chatService.chatStreamBySentence(session, text, false,
                        (sentence, isFirst, isLast) -> {
                            handleSentence(
                                    session,
                                    sentence,
                                    isFirst,
                                    isLast);
                        });
            });
        } catch (Exception e) {
            logger.error("处理唤醒词失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理文本消息交互
     * 如果指定了输出文本，则用指定的文本生成语音
     * 
     * @param session
     * @param inputText    输入文本
     * @param textConsumer 具体处理输入文本，传入 dialogId
     */
    public void handleText(ChatSession session, String inputText, Consumer<Long> textConsumer) {
        // 初始化对话状态
        String sessionId = session.getSessionId();
        initChat(sessionId);
        Thread.startVirtualThread(() -> {
            try {
                SysDevice device = sessionManager.getDeviceConfig(sessionId);
                if (device == null) {
                    return;
                }
                sessionManager.updateLastActivity(sessionId);
                // 设置用户消息的创建时间戳，要在消息入库前获得时间戳。 后续考虑:传递的消息不一定是String，也可以是封装的。
                final Long userTimeMillis =  System.currentTimeMillis();
                session.setUserTimeMillis(userTimeMillis);
                // 发送识别结果
                messageService.sendSttMessage(session, inputText);
                audioService.sendStart(session);

                // 设置LLM生成消息的时间戳作为Assistant消息的创建时间戳，也用于约定保存音频文件的路径。一定要在LLM前设置时间戳。
                final Long assistantTimeMillis = System.currentTimeMillis();
                session.setAssistantTimeMillis(assistantTimeMillis);

                if (textConsumer != null) {
                    // 如果指定了输出文本，则直接使用指定的文本生成语音
                    // TODO 重新思考这个textConsumer的作用。
                    textConsumer.accept(assistantTimeMillis);
                } else {
                    logger.info("处理聊天文字输入: \"{}\"", inputText);
                    // 使用句子切分处理流式响应
                    chatService.chatStreamBySentence(session, inputText, true,
                            (sentence, isFirst, isLast) -> {
                                handleSentence(
                                        session,
                                        sentence,
                                        isFirst,
                                        isLast);
                            });
                }
            } catch (Exception e) {
                logger.error("处理唤醒词失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 中止当前对话
     */
    public void abortDialogue(ChatSession session, String reason) {
        Thread.startVirtualThread(() -> {
            try {
                String sessionId = session.getSessionId();
                logger.info("中止对话 - SessionId: {}, Reason: {}", sessionId, reason);

                // 关闭音频流
                sessionManager.closeAudioStream(sessionId);
                sessionManager.setStreamingState(sessionId, false);

                if (sessionManager.isMusicPlaying(sessionId)) {
                    musicService.stopMusic(sessionId);
                    huiBenService.stopHuiBen(sessionId);
                    return;
                }
                // 清空句子队列
                CopyOnWriteArrayList<Sentence> queue = sentenceQueue.get(sessionId);
                if (queue != null) {
                    queue.clear();
                }

                // 重置首句处理状态
                AtomicBoolean firstDone = firstSentDone.get(sessionId);
                if (firstDone != null) {
                    firstDone.set(false);
                }

                // 清理TTS任务队列和信号量
                PriorityBlockingQueue<TtsTask> taskQueue = sessionTaskQueues.get(sessionId);
                if (taskQueue != null) {
                    taskQueue.clear();
                    logger.info("已清空TTS任务队列 - SessionId: {}", sessionId);
                }
                
                // 释放所有信号量许可，确保正在进行的TTS任务能够完成
                Semaphore semaphore = sessionSemaphores.get(sessionId);
                if (semaphore != null) {
                    // 释放所有可能的许可，让正在进行的任务能够完成
                    semaphore.release(MAX_CONCURRENT_PER_SESSION);
                    logger.info("已释放TTS信号量许可 - SessionId: {}", sessionId);
                }

                // 终止语音发送
                audioService.sendStop(session);
            } catch (Exception e) {
                logger.error("中止对话失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 清理会话资源
     */
    public void cleanupSession(String sessionId) {
        seqCounters.remove(sessionId);
        sttStartTimes.remove(sessionId);
        llmStartTimes.remove(sessionId);
        sentenceQueue.remove(sessionId);
        firstSentDone.remove(sessionId);
        locks.remove(sessionId);

        // 新增：清理并发控制相关资源
        sessionSemaphores.remove(sessionId);
        PriorityBlockingQueue<TtsTask> taskQueue = sessionTaskQueues.remove(sessionId);
        if (taskQueue != null) {
            taskQueue.clear();
        }

        // 清理AudioService中的资源
        audioService.cleanupSession(sessionId);
    }

}
package com.xiaozhi.websocket.service;

import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.OpusProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 音频服务 - 负责音频处理和WebSocket发送
 */
@Service
public class AudioService {

    private static final Logger logger = LoggerFactory.getLogger(AudioService.class);

    // 播放帧持续时间（毫秒）
    private static final int FRAME_DURATION_MS = 60;

    // 默认音频采样率和通道数
    private static final int DEFAULT_SAMPLE_RATE = 16000;
    private static final int DEFAULT_CHANNELS = 1;

    // 为每个会话维护一个音频发送队列
    private final Map<String, Queue<AudioMessageTask>> sessionAudioQueues = new ConcurrentHashMap<>();

    // 跟踪每个会话的处理状态
    private final Map<String, AtomicBoolean> sessionProcessingFlags = new ConcurrentHashMap<>();

    // 跟踪每个会话的消息序列号，用于日志
    private final Map<String, AtomicInteger> sessionMessageCounters = new ConcurrentHashMap<>();

    // 为流式TTS任务维护一个队列
    private final Map<String, Queue<StreamingAudioTask>> sessionStreamingQueues = new ConcurrentHashMap<>();

    // 跟踪每个会话的流式处理状态
    private final Map<String, AtomicBoolean> sessionStreamingFlags = new ConcurrentHashMap<>();

    @Autowired
    private OpusProcessor opusProcessor;

    @Autowired
    private SessionManager sessionManager;

    private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    /**
     * 音频消息任务类，包含需要发送的音频数据和元信息
     */
    private static class AudioMessageTask {
        private final List<byte[]> opusFrames;
        private final String text;
        private final boolean isFirstMessage;
        private final boolean isLastMessage;
        private final String audioFilePath;
        private final int sequenceNumber; // 添加序列号用于日志跟踪

        public AudioMessageTask(List<byte[]> opusFrames, String text, boolean isFirstMessage, boolean isLastMessage,
                String audioFilePath, int sequenceNumber) {
            this.opusFrames = opusFrames;
            this.text = text;
            this.isFirstMessage = isFirstMessage;
            this.isLastMessage = isLastMessage;
            this.audioFilePath = audioFilePath;
            this.sequenceNumber = sequenceNumber;
        }

        public List<byte[]> getOpusFrames() {
            return opusFrames;
        }

        public String getText() {
            return text;
        }

        public boolean isFirstMessage() {
            return isFirstMessage;
        }

        public boolean isLastMessage() {
            return isLastMessage;
        }

        public String getAudioFilePath() {
            return audioFilePath;
        }

        public int getSequenceNumber() {
            return sequenceNumber;
        }
    }

    /**
     * 流式音频任务类
     */
    private static class StreamingAudioTask {
        private final String text;
        private final boolean isFirstMessage;
        private final boolean isLastMessage;
        private final SysConfig ttsConfig;
        private final String voiceName;
        private final int sequenceNumber;

        public StreamingAudioTask(String text, boolean isFirstMessage, boolean isLastMessage,
                SysConfig ttsConfig, String voiceName, int sequenceNumber) {
            this.text = text;
            this.isFirstMessage = isFirstMessage;
            this.isLastMessage = isLastMessage;
            this.ttsConfig = ttsConfig;
            this.voiceName = voiceName;
            this.sequenceNumber = sequenceNumber;
        }

        public String getText() {
            return text;
        }

        public boolean isFirstMessage() {
            return isFirstMessage;
        }

        public boolean isLastMessage() {
            return isLastMessage;
        }

        public SysConfig getTtsConfig() {
            return ttsConfig;
        }

        public String getVoiceName() {
            return voiceName;
        }

        public int getSequenceNumber() {
            return sequenceNumber;
        }
    }

    /**
     * 音频处理结果类
     */
    public static class AudioProcessResult {
        private final List<byte[]> opusFrames;
        private final long durationMs;

        public AudioProcessResult() {
            this.opusFrames = new ArrayList<>();
            this.durationMs = 0;
        }

        public AudioProcessResult(List<byte[]> opusFrames, long durationMs) {
            this.opusFrames = opusFrames;
            this.durationMs = durationMs;
        }

        public List<byte[]> getOpusFrames() {
            return opusFrames;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }

    /**
     * 初始化会话的音频处理
     * 
     * @param sessionId WebSocket会话ID
     */
    public void initializeSession(String sessionId) {
        sessionAudioQueues.putIfAbsent(sessionId, new ConcurrentLinkedQueue<>());
        sessionProcessingFlags.putIfAbsent(sessionId, new AtomicBoolean(false));
        sessionMessageCounters.putIfAbsent(sessionId, new AtomicInteger(0));
        sessionStreamingQueues.putIfAbsent(sessionId, new ConcurrentLinkedQueue<>());
        sessionStreamingFlags.putIfAbsent(sessionId, new AtomicBoolean(false));
        sessionManager.updateLastActivity(sessionId);
    }

    /**
     * 清理会话的音频处理状态
     * 
     * @param sessionId WebSocket会话ID
     */
    public void cleanupSession(String sessionId) {
        // 清理Opus处理器的会话状态
        opusProcessor.cleanupSession(sessionId);

        // 清理音频队列
        Queue<AudioMessageTask> queue = sessionAudioQueues.get(sessionId);
        if (queue != null) {
            // 收集需要删除的音频文件
            List<String> filesToDelete = new ArrayList<>();
            queue.forEach(task -> {
                if (task != null && task.getAudioFilePath() != null) {
                    filesToDelete.add(task.getAudioFilePath());
                }
            });

            // 清空队列
            queue.clear();

            // 异步删除音频文件
            if (!filesToDelete.isEmpty()) {
                Mono.fromRunnable(() -> filesToDelete.forEach(this::deleteAudioFiles))
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
            }
        }

        // 清空流式队列
        Queue<StreamingAudioTask> streamingQueue = sessionStreamingQueues.get(sessionId);
        if (streamingQueue != null) {
            streamingQueue.clear();
        }

        // 重置处理状态
        AtomicBoolean isProcessing = sessionProcessingFlags.get(sessionId);
        if (isProcessing != null) {
            isProcessing.set(false);
        }

        AtomicBoolean isStreamingProcessing = sessionStreamingFlags.get(sessionId);
        if (isStreamingProcessing != null) {
            isStreamingProcessing.set(false);
        }

        // 重置消息计数器
        sessionMessageCounters.remove(sessionId);

        // 移除会话相关的映射
        sessionAudioQueues.remove(sessionId);
        sessionProcessingFlags.remove(sessionId);
        sessionStreamingQueues.remove(sessionId);
        sessionStreamingFlags.remove(sessionId);

    }

    /**
     * 处理音频文件，提取PCM数据并转换为Opus格式
     * 
     * @param audioFilePath 音频文件路径
     * @param sampleRate    采样率
     * @param channels      通道数
     * @return 处理结果，包含opus数据和持续时间
     */
    public AudioProcessResult processAudioFile(String sessionId, String audioFilePath, int sampleRate, int channels) {
        try {
            // 从音频文件获取PCM数据
            byte[] pcmData = extractPcmFromAudio(audioFilePath);
            if (pcmData == null) {
                logger.error("无法从文件提取PCM数据: {}", audioFilePath);
                return new AudioProcessResult();
            }

            // 计算音频时长
            long durationMs = calculateAudioDuration(pcmData, sampleRate, channels);

            // 转换为Opus格式
            List<byte[]> opusFrames = opusProcessor.convertPcmToOpus(sessionId, pcmData, sampleRate, channels, FRAME_DURATION_MS);

            return new AudioProcessResult(opusFrames, durationMs);
        } catch (Exception e) {
            logger.error("处理音频文件失败: {}", audioFilePath, e);
            return new AudioProcessResult();
        }
    }

    /**
     * 从音频文件中提取PCM数据
     * 
     * @param audioFilePath 音频文件路径
     * @return PCM格式的音频数据
     */
    public byte[] extractPcmFromAudio(String audioFilePath) {
        try {
            // 创建临时PCM文件
            File tempPcmFile = File.createTempFile("temp_pcm_extract_", ".pcm");
            String tempPcmPath = tempPcmFile.getAbsolutePath();
            try {
                // 使用FFmpeg直接将音频转换为PCM
                String[] command = {
                        "ffmpeg",
                        "-i", audioFilePath,
                        "-f", "s16le", // 16位有符号小端格式
                        "-acodec", "pcm_s16le",
                        "-y",
                        tempPcmPath
                };

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                // 等待进程完成
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    logger.error("FFmpeg提取PCM数据失败，退出码: {}", exitCode);
                    return null;
                }

                // 读取PCM文件内容
                return Files.readAllBytes(Paths.get(tempPcmPath));
            } finally {
                // 删除临时文件
                tempPcmFile.delete();
            }
        } catch (Exception e) {
            logger.error("从音频文件提取PCM数据失败: {}", audioFilePath, e);
            return null;
        }
    }

    /**
     * 计算音频时长
     * 
     * @param pcmData    PCM格式的音频数据
     * @param sampleRate 采样率
     * @param channels   通道数
     * @return 音频时长（毫秒）
     */
    public long calculateAudioDuration(byte[] pcmData, int sampleRate, int channels) {
        // 16位采样
        int bytesPerSample = 2;
        return (long) ((pcmData.length * 1000.0) / (sampleRate * channels * bytesPerSample));
    }

    /**
     * 发送音频消息
     * 
     * @param session       WebSocket会话
     * @param audioFilePath 音频文件路径
     * @param text          文本内容
     * @param isStart       是否是整个对话的开始
     * @param isEnd         是否是整个对话的结束
     * @return Mono<Void> 操作结果
     */
    public Mono<Void> sendAudioMessage(WebSocketSession session, String audioFilePath, String text, boolean isStart,
            boolean isEnd) {
        String sessionId = session.getId();

        // 确保会话已初始化
        initializeSession(sessionId);

        // 获取消息序列号
        int sequenceNumber = sessionMessageCounters.get(sessionId).incrementAndGet();

        // 处理音频文件，转换为Opus格式
        return Mono.fromCallable(() -> processAudioFile(sessionId, audioFilePath, DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(audioResult -> {
                    // 将任务添加到队列
                    AudioMessageTask task = new AudioMessageTask(
                            audioResult.getOpusFrames(),
                            text,
                            isStart,
                            isEnd,
                            audioFilePath,
                            sequenceNumber);

                    Queue<AudioMessageTask> queue = sessionAudioQueues.get(sessionId);
                    queue.add(task);

                    // 如果当前没有正在处理的任务，开始处理队列
                    AtomicBoolean isProcessing = sessionProcessingFlags.get(sessionId);
                    if (isProcessing.compareAndSet(false, true)) {
                        return processAudioQueue(session, sessionId);
                    }

                    // 如果已经有任务在处理，直接返回
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    logger.error("[消息#{}-错误] 准备音频消息失败: {}", sequenceNumber, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * 处理流式音频数据并发送
     * 
     * @param session   WebSocket会话
     * @param text      文本内容
     * @param isStart   是否是对话开始
     * @param isEnd     是否是对话结束
     * @param config    TTS配置
     * @param voiceName 语音名称
     * @return 操作结果
     */
    public Mono<Void> streamAudioMessage(WebSocketSession session, String text, boolean isStart, boolean isEnd,
            SysConfig config, String voiceName) {
        String sessionId = session.getId();

        // 确保会话已初始化
        initializeSession(sessionId);

        // 获取消息序列号
        int sequenceNumber = sessionMessageCounters.get(sessionId).incrementAndGet();

        logger.info("添加流式TTS任务到队列 - 文本: {}, 序列号: {}", text, sequenceNumber);

        // 创建新的流式任务并添加到队列
        StreamingAudioTask task = new StreamingAudioTask(text, isStart, isEnd, config, voiceName, sequenceNumber);
        Queue<StreamingAudioTask> queue = sessionStreamingQueues.get(sessionId);
        queue.add(task);

        // 如果当前没有正在处理的任务，开始处理队列
        AtomicBoolean isProcessing = sessionStreamingFlags.get(sessionId);
        if (isProcessing.compareAndSet(false, true)) {
            return null;
        }

        // 如果已经有任务在处理，直接返回
        return Mono.empty();
    }

    /**
     * 处理音频队列中的任务
     * 
     * @param session   WebSocket会话
     * @param sessionId 会话ID
     * @return 处理结果
     */
    private Mono<Void> processAudioQueue(WebSocketSession session, String sessionId) {
        Queue<AudioMessageTask> queue = sessionAudioQueues.get(sessionId);
        AtomicBoolean isProcessing = sessionProcessingFlags.get(sessionId);

        // 如果队列为空或会话已关闭，结束处理
        if (queue.isEmpty() || !session.isOpen()) {
            isProcessing.set(false);
            return Mono.empty();
        }

        // 获取下一个任务
        AudioMessageTask task = queue.poll();
        int sequenceNumber = task.getSequenceNumber();

        // 创建消息序列
        List<Mono<Void>> messageSequence = new ArrayList<>();

        // 1. 如果是第一条消息，发送开始标记
        if (task.isFirstMessage()) {
            messageSequence.add(sendTtsStartMessage(session, sequenceNumber));
        }

        // 2. 如果有文本，发送句子开始标记
        if (task.getText() != null && !task.getText().isEmpty()) {
            messageSequence.add(sendSentenceStartMessage(session, task.getText(), sequenceNumber));
        }

        // 3. 准备音频数据消息
        List<WebSocketMessage> audioMessages = new ArrayList<>();
        for (byte[] frame : task.getOpusFrames()) {
            DataBuffer buffer = bufferFactory.wrap(frame);
            audioMessages.add(session.binaryMessage(factory -> buffer));
        }

        // 4. 添加音频发送操作到序列
        if (!audioMessages.isEmpty()) {
            messageSequence.add(
                    session.send(
                            Flux.fromIterable(audioMessages)
                                    .delayElements(Duration.ofMillis(FRAME_DURATION_MS))));
        }

        // 6. 如果是最后一条消息，发送结束标记
        if (task.isLastMessage()) {
            messageSequence.add(sendTtsStopMessage(session, sequenceNumber));
        }

        // 执行所有消息发送操作
        return Flux.concat(messageSequence)
                .then()
                .doFinally(signalType -> {
                    // 删除音频文件
                    deleteAudioFiles(task.getAudioFilePath());

                    // 继续处理队列中的下一个任务
                    if (!queue.isEmpty() && session.isOpen()) {
                        processAudioQueue(session, sessionId)
                                .subscribe();
                    } else {
                        // 队列为空，重置处理状态
                        isProcessing.set(false);
                    }
                });
    }

    /**
     * 删除音频文件及其相关文件（如同名的VTT文件）
     * 
     * @param audioPath 音频文件路径
     * @return 是否成功删除
     */
    public boolean deleteAudioFiles(String audioPath) {
        if (audioPath == null) {
            return false;
        }
        // 这边后续应该把所有音频文件合并起来，先不删除音频文件
        return true;
        /*
         * try {
         * boolean success = true;
         * 
         * // 删除原始音频文件
         * File audioFile = new File(audioPath);
         * if (audioFile.exists()) {
         * if (!audioFile.delete()) {
         * logger.warn("无法删除音频文件: {}", audioPath);
         * success = false;
         * }
         * }
         * 
         * // 删除可能存在的VTT文件
         * File vttFile = new File(audioPath + ".vtt");
         * if (vttFile.exists()) {
         * if (!vttFile.delete()) {
         * logger.warn("无法删除VTT文件: {}", vttFile.getPath());
         * success = false;
         * }
         * }
         * 
         * return success;
         * } catch (Exception e) {
         * logger.error("删除音频文件时发生错误: {}", audioPath, e);
         * return false;
         * }
         */
    }

    /**
     * 发送TTS句子开始消息（包含文本）
     * 
     * @param session WebSocket会话
     * @param text    句子文本
     * @return 操作结果
     */
    public Mono<Void> sendSentenceStartMessage(WebSocketSession session, String text, int sequenceNumber) {
        try {
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\"type\":\"tts\",\"state\":\"sentence_start\"");
            if (text != null && !text.isEmpty()) {
                jsonBuilder.append(",\"text\":\"").append(text.replace("\"", "\\\"")).append("\"");
            }
            jsonBuilder.append("}");

            String message = jsonBuilder.toString();

            return session.send(Mono.just(session.textMessage(message)));
        } catch (Exception e) {
            logger.error("[消息#{}-错误] 发送句子开始消息失败", sequenceNumber, e);
            return Mono.empty();
        }
    }

    /**
     * 发送TTS开始消息
     * 
     * @param session WebSocket会话
     * @return 操作结果
     */
    public Mono<Void> sendTtsStartMessage(WebSocketSession session, int sequenceNumber) {
        try {
            String message = "{\"type\":\"tts\",\"state\":\"start\"}";
            return session.send(Mono.just(session.textMessage(message)));
        } catch (Exception e) {
            return Mono.empty();
        }
    }

    /**
     * 发送TTS停止消息
     * 
     * @param session WebSocket会话
     * @return 操作结果
     */
    public Mono<Void> sendTtsStopMessage(WebSocketSession session, int sequenceNumber) {
        if (session == null || !session.isOpen()) {
            return Mono.empty();
        }

        String sessionId = session.getId();

        // 清空音频队列
        Queue<AudioMessageTask> queue = sessionAudioQueues.get(sessionId);
        if (queue != null) {
            // 保存需要删除的音频文件路径
            List<String> filesToDelete = new ArrayList<>();
            queue.forEach(task -> {
                if (task != null && task.getAudioFilePath() != null) {
                    filesToDelete.add(task.getAudioFilePath());
                }
            });

            // 清空队列
            queue.clear();

            // 异步删除文件
            if (!filesToDelete.isEmpty()) {
                Mono.fromRunnable(() -> filesToDelete.forEach(this::deleteAudioFiles))
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
            }
        }

        // 发送停止指令
        try {
            String message = "{\"type\":\"tts\",\"state\":\"stop\"}";
            return session.send(Mono.just(session.textMessage(message)));
        } catch (Exception e) {
            logger.error("[消息#{}-错误] 发送TTS停止消息失败", sequenceNumber, e);
            return Mono.empty();
        }
    }

    /**
     * 立即停止音频发送，用于处理中断请求
     * 
     * @param session WebSocket会话
     * @return 操作结果
     */
    public Mono<Void> sendStop(WebSocketSession session) {
        String sessionId = session.getId();
        int sequenceNumber = sessionMessageCounters.getOrDefault(sessionId, new AtomicInteger(0)).incrementAndGet();

        // 清空流式队列
        Queue<StreamingAudioTask> streamingQueue = sessionStreamingQueues.get(sessionId);
        if (streamingQueue != null) {
            streamingQueue.clear();
        }

        // 重置流式处理状态
        AtomicBoolean isStreamingProcessing = sessionStreamingFlags.get(sessionId);
        if (isStreamingProcessing != null) {
            isStreamingProcessing.set(false);
        }

        // 复用现有的停止方法
        return sendTtsStopMessage(session, sequenceNumber);
    }

    /**
     * 为向后兼容保留的方法
     */
    public Mono<Void> sendSentenceStartMessage(WebSocketSession session, String text) {
        String sessionId = session.getId();
        int sequenceNumber = sessionMessageCounters.getOrDefault(sessionId, new AtomicInteger(0)).incrementAndGet();
        return sendSentenceStartMessage(session, text, sequenceNumber);
    }

    /**
     * 为向后兼容保留的方法
     */
    public Mono<Void> sendTtsStartMessage(WebSocketSession session) {
        String sessionId = session.getId();
        int sequenceNumber = sessionMessageCounters.getOrDefault(sessionId, new AtomicInteger(0)).incrementAndGet();
        return sendTtsStartMessage(session, sequenceNumber);
    }

    /**
     * 为向后兼容保留的方法
     */
    public Mono<Void> sendTtsStopMessage(WebSocketSession session) {
        String sessionId = session.getId();
        int sequenceNumber = sessionMessageCounters.getOrDefault(sessionId, new AtomicInteger(0)).incrementAndGet();
        return sendTtsStopMessage(session, sequenceNumber);
    }
}
package com.xiaozhi.websocket.service;

import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.websocket.llm.LlmManager;
import com.xiaozhi.websocket.service.VadService.VadStatus;
import com.xiaozhi.websocket.stt.SttService;
import com.xiaozhi.websocket.stt.factory.SttServiceFactory;
import com.xiaozhi.websocket.tts.factory.TtsServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 对话处理服务
 * 负责处理语音识别和对话生成的业务逻辑
 */
@Service
public class DialogueService {
    private static final Logger logger = LoggerFactory.getLogger(DialogueService.class);

    // 用于格式化浮点数，保留2位小数
    private static final DecimalFormat df = new DecimalFormat("0.00");

    @Autowired
    private LlmManager llmManager;

    @Autowired
    private AudioService audioService;

    @Autowired
    private TtsServiceFactory ttsService;

    @Autowired
    private SttServiceFactory sttServiceFactory;

    @Autowired
    private MessageService messageService;

    @Autowired
    private VadService vadService;

    @Autowired
    private SessionManager sessionManager;

    // 添加一个每个会话的句子序列号计数器
    private final Map<String, AtomicInteger> sessionSentenceCounters = new ConcurrentHashMap<>();

    // 添加会话的语音识别开始时间记录
    private final Map<String, Long> sessionSttStartTimes = new ConcurrentHashMap<>();

    // 添加会话的模型回复开始时间记录
    private final Map<String, Long> sessionLlmStartTimes = new ConcurrentHashMap<>();

    // 添加会话的完整回复内容
    private final Map<String, StringBuilder> sessionFullResponses = new ConcurrentHashMap<>();
    
    // 添加会话的音频生成任务列表，用于跟踪并发生成的音频任务
    private final Map<String, List<CompletableFuture<String>>> sessionAudioTasks = new ConcurrentHashMap<>();
    
    // 添加会话的句子顺序列表，确保按顺序发送
    private final Map<String, List<PendingSentence>> sessionPendingSentences = new ConcurrentHashMap<>();

    /**
     * 待处理句子类，用于按顺序发送句子
     */
    private static class PendingSentence {
        private final int sentenceNumber;
        private final String sentence;
        private final boolean isStart;
        private final boolean isEnd;
        private CompletableFuture<String> audioFuture;

        public PendingSentence(int sentenceNumber, String sentence, boolean isStart, boolean isEnd) {
            this.sentenceNumber = sentenceNumber;
            this.sentence = sentence;
            this.isStart = isStart;
            this.isEnd = isEnd;
            this.audioFuture = null;
        }
        
        public void setAudioFuture(CompletableFuture<String> audioFuture) {
            this.audioFuture = audioFuture;
        }
    }

    /**
     * 处理音频数据
     * 
     * @param session  WebSocket会话
     * @param opusData Opus格式的音频数据
     * @return 处理结果
     */
    public Mono<Void> processAudioData(WebSocketSession session, byte[] opusData) {
        String sessionId = session.getId();
        SysDevice device = sessionManager.getDeviceConfig(sessionId);

        // 如果设备未注册或不在监听状态，忽略音频数据
        if (device == null || !sessionManager.isListening(sessionId)) {
            return Mono.empty();
        }

        SysConfig sttConfig = null;
        SysConfig ttsConfig = null;

        if (device.getSttId() != null) {
            sttConfig = sessionManager.getCachedConfig(device.getSttId());
        }

        if (device.getTtsId() != null) {
            ttsConfig = sessionManager.getCachedConfig(device.getTtsId());
        }

        // 创建最终变量以在lambda中使用
        final SysConfig finalSttConfig = sttConfig;
        final SysConfig finalTtsConfig = ttsConfig;

        return Mono.fromCallable(() -> {
            // 使用VAD处理音频数据
            return vadService.processAudio(sessionId, opusData);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(vadResult -> {
                    // 如果VAD处理出错，直接返回
                    if (vadResult.getStatus() == VadStatus.ERROR || vadResult.getProcessedData() == null) {
                        return Mono.empty();
                    }
                    // 检测到语音
                    sessionManager.updateLastActivity(sessionId);
                    // 根据VAD状态处理
                    switch (vadResult.getStatus()) {
                        case SPEECH_START:
                            // 检测到语音开始，记录开始时间
                            sessionSttStartTimes.put(sessionId, System.currentTimeMillis());
                            logger.info("语音识别开始 - SessionId: {}", sessionId);

                            // 初始化流式识别
                            return initializeStreamingRecognition(session, sessionId, finalSttConfig, finalTtsConfig,
                                    device,
                                    vadResult.getProcessedData());

                        case SPEECH_CONTINUE:
                            // 语音继续，发送数据到流式识别
                            if (sessionManager.isStreaming(sessionId)) {
                                Sinks.Many<byte[]> audioSink = sessionManager.getAudioSink(sessionId);
                                if (audioSink != null) {
                                    audioSink.tryEmitNext(vadResult.getProcessedData());
                                }
                            }
                            return Mono.empty();

                        case SPEECH_END:
                            // 语音结束，完成流式识别
                            if (sessionManager.isStreaming(sessionId)) {
                                Sinks.Many<byte[]> audioSink = sessionManager.getAudioSink(sessionId);
                                if (audioSink != null) {
                                    audioSink.tryEmitComplete();
                                    sessionManager.setStreamingState(sessionId, false);
                                }
                            }
                            return Mono.empty();

                        default:
                            return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    logger.error("处理音频数据失败: {}", e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * 初始化流式语音识别
     */
    private Mono<Void> initializeStreamingRecognition(
            WebSocketSession session,
            String sessionId,
            SysConfig sttConfig,
            SysConfig ttsConfig,
            SysDevice device,
            byte[] initialAudio) {

        // 如果已经在进行流式识别，先清理旧的资源
        sessionManager.closeAudioSink(sessionId);

        // 创建新的音频数据接收器
        Sinks.Many<byte[]> audioSink = sessionManager.createAudioSink(sessionId);
        sessionManager.setStreamingState(sessionId, true);

        // 获取对应的STT服务
        SttService sttService = sttServiceFactory.getSttService(sttConfig);

        if (sttService == null) {
            logger.error("无法获取STT服务 - Provider: {}", sttConfig != null ? sttConfig.getProvider() : "null");
            return Mono.empty();
        }

        // 发送初始音频数据
        if (initialAudio != null && initialAudio.length > 0) {
            audioSink.tryEmitNext(initialAudio);
        }

        // 创建最终变量以在lambda中使用
        final SysConfig finalTtsConfig = ttsConfig;

        // 启动流式识别
        sttService.streamRecognition(audioSink.asFlux())
                .doOnNext(text -> {
                    // 发送中间识别结果
                    if (StringUtils.hasText(text)) {
                        messageService.sendMessage(session, "stt", "interim", text).subscribe();
                    }
                })
                .defaultIfEmpty("")
                .last() // 获取最终结果
                .flatMap(finalText -> {
                    if (!StringUtils.hasText(finalText)) {
                        return Mono.empty();
                    }

                    // 记录语音识别完成时间并计算用时
                    long sttEndTime = System.currentTimeMillis();
                    Long sttStartTime = sessionSttStartTimes.get(sessionId);
                    if (sttStartTime != null) {
                        double sttDuration = (sttEndTime - sttStartTime) / 1000.0;
                        logger.info("语音识别完成 - SessionId: {}, 用时: {}秒, 识别结果: \"{}\"",
                                sessionId, df.format(sttDuration), finalText);
                    }

                    // 记录模型回复开始时间
                    sessionLlmStartTimes.put(sessionId, System.currentTimeMillis());

                    // 初始化完整回复内容
                    sessionFullResponses.put(sessionId, new StringBuilder());

                    // 设置会话为非监听状态，防止处理自己的声音
                    sessionManager.setListeningState(sessionId, false);

                    // 初始化句子计数器
                    sessionSentenceCounters.putIfAbsent(sessionId, new AtomicInteger(0));
                    
                    // 初始化音频任务列表和待处理句子列表
                    sessionAudioTasks.putIfAbsent(sessionId, new ArrayList<>());
                    sessionPendingSentences.putIfAbsent(sessionId, new ArrayList<>());

                    // 发送最终识别结果
                    return messageService.sendMessage(session, "stt", "final", finalText)
                            .then(Mono.fromRunnable(() -> {
                                // 使用句子切分处理流式响应
                                llmManager.chatStreamBySentence(device, finalText,
                                        (sentence, isStart, isEnd) -> {
                                            processSentenceFromLlm(
                                                session, 
                                                sessionId, 
                                                sentence, 
                                                isStart, 
                                                isEnd, 
                                                finalTtsConfig, 
                                                device.getVoiceName()
                                            );
                                        });
                            }));
                })
                .onErrorResume(error -> {
                    logger.error("流式识别错误: {}", error.getMessage(), error);
                    return Mono.empty();
                })
                .subscribe();

        return Mono.empty();
    }

    /**
     * 处理从LLM接收到的句子
     */
    private void processSentenceFromLlm(
            WebSocketSession session,
            String sessionId,
            String sentence,
            boolean isStart,
            boolean isEnd,
            SysConfig ttsConfig,
            String voiceName) {
        
        // 获取句子序列号
        int sentenceNumber = sessionSentenceCounters.get(sessionId).incrementAndGet();
        
        // 累加完整回复内容
        sessionFullResponses.get(sessionId).append(sentence);
        
        // 计算模型响应时间
        double modelResponseTime = 0.00;
        Long llmStartTime = sessionLlmStartTimes.get(sessionId);
        if (llmStartTime != null) {
            modelResponseTime = (System.currentTimeMillis() - llmStartTime) / 1000.0;
        }
        
        // 创建待处理句子对象
        PendingSentence pendingSentence = new PendingSentence(sentenceNumber, sentence, isStart, isEnd);
        
        // 添加到待处理句子列表
        List<PendingSentence> pendingSentences = sessionPendingSentences.get(sessionId);
        synchronized (pendingSentences) {
            pendingSentences.add(pendingSentence);
        }
        
        // 并发生成音频
        CompletableFuture<String> audioFuture = generateAudioAsync(sessionId, sentence, ttsConfig, voiceName, sentenceNumber, modelResponseTime);
        
        // 设置句子的音频Future
        pendingSentence.setAudioFuture(audioFuture);
        
        // 添加到音频任务列表
        List<CompletableFuture<String>> audioTasks = sessionAudioTasks.get(sessionId);
        synchronized (audioTasks) {
            audioTasks.add(audioFuture);
        }
        
        // 检查是否可以发送句子
        checkAndSendPendingSentences(session, sessionId);
    }
    
    /**
     * 异步生成音频
     */
    private CompletableFuture<String> generateAudioAsync(
            String sessionId,
            String sentence,
            SysConfig ttsConfig,
            String voiceName,
            int sentenceNumber,
            double modelResponseTime) {
        
        // 记录TTS开始时间
        long ttsStartTime = System.currentTimeMillis();
        
        // 创建CompletableFuture
        CompletableFuture<String> future = new CompletableFuture<>();
        
        // 异步生成音频
        CompletableFuture.runAsync(() -> {
            try {
                // 调用TTS服务生成音频
                String audioPath = ttsService.getTtsService(ttsConfig, voiceName).textToSpeech(sentence);
                
                // 计算TTS处理用时
                long ttsDuration = System.currentTimeMillis() - ttsStartTime;
                
                // 记录日志
                logger.info("序号: {}, 模型回复: {}秒, 语音生成: {}秒, 内容: \"{}\"",
                        sentenceNumber, df.format(modelResponseTime), df.format(ttsDuration / 1000.0), sentence);
                
                // 完成Future
                future.complete(audioPath);
                
                // 检查是否可以发送句子
                checkAndSendPendingSentences(null, sessionId);
            } catch (Exception e) {
                logger.error("生成音频失败 - 句子序号: {}, 错误: {}", sentenceNumber, e.getMessage(), e);
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    /**
     * 检查并发送待处理的句子
     */
    private synchronized void checkAndSendPendingSentences(WebSocketSession session, String sessionId) {
        // 如果没有提供session，尝试从SessionManager获取
        if (session == null) {
            session = sessionManager.getSession(sessionId);
            if (session == null || !session.isOpen()) {
                return;
            }
        }
        
        // 获取待处理句子列表
        List<PendingSentence> pendingSentences = sessionPendingSentences.get(sessionId);
        if (pendingSentences == null || pendingSentences.isEmpty()) {
            return;
        }
        
        // 创建最终变量以在lambda中使用
        final WebSocketSession finalSession = session;
        
        // 检查并按顺序发送句子
        synchronized (pendingSentences) {
            // 使用迭代器安全地遍历和删除元素
            Iterator<PendingSentence> iterator = pendingSentences.iterator();
            List<PendingSentence> processedSentences = new ArrayList<>();
            
            while (iterator.hasNext()) {
                PendingSentence sentence = iterator.next();
                
                // 如果音频未准备好，停止处理
                if (sentence.audioFuture == null || !sentence.audioFuture.isDone()) {
                    break;
                }
                
                try {
                    // 获取音频路径
                    String audioPath = sentence.audioFuture.get();
                    
                    // 发送音频消息
                    audioService.sendAudioMessage(
                            finalSession, 
                            audioPath, 
                            sentence.sentence, 
                            sentence.isStart, 
                            sentence.isEnd
                    ).subscribe();
                    
                    // 记录已处理的句子，稍后一次性删除
                    processedSentences.add(sentence);
                    
                    // 如果是最后一个句子，记录完整回复
                    if (sentence.isEnd) {
                        StringBuilder fullResponse = sessionFullResponses.get(sessionId);
                        if (fullResponse != null) {
                            logger.info("对话完成 - SessionId: {}, 完整回复: \"{}\"", sessionId, fullResponse.toString());
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理音频失败 - 句子序号: {}, 错误: {}", sentence.sentenceNumber, e.getMessage(), e);
                    // 记录失败的句子，稍后一次性删除
                    processedSentences.add(sentence);
                }
            }
            
            // 一次性从列表中删除所有已处理的句子
            pendingSentences.removeAll(processedSentences);
        }
    }

    /**
     * 处理语音唤醒
     */
    public Mono<Void> handleWakeWord(WebSocketSession session, String text) {
        String sessionId = session.getId();
        SysDevice device = sessionManager.getDeviceConfig(sessionId);

        if (device == null) {
            return Mono.empty();
        }

        // 获取配置并创建final变量以在lambda中使用
        final SysConfig ttsConfig = device.getTtsId() != null ? sessionManager.getCachedConfig(device.getTtsId())
                : null;
        sessionManager.updateLastActivity(sessionId);
        logger.info("检测到唤醒词: \"{}\"", text);

        // 记录模型回复开始时间
        sessionLlmStartTimes.put(sessionId, System.currentTimeMillis());

        // 初始化完整回复内容
        sessionFullResponses.put(sessionId, new StringBuilder());

        // 设置为非监听状态，防止处理自己的声音
        sessionManager.setListeningState(sessionId, false);

        // 初始化句子计数器
        sessionSentenceCounters.putIfAbsent(sessionId, new AtomicInteger(0));
        
        // 初始化音频任务列表和待处理句子列表
        sessionAudioTasks.putIfAbsent(sessionId, new ArrayList<>());
        sessionPendingSentences.putIfAbsent(sessionId, new ArrayList<>());

        // 发送识别结果
        messageService.sendMessage(session, "stt", "start", text).subscribe();

        // 使用句子切分处理流式响应
        return Mono.fromRunnable(() -> {
            // 使用句子切分处理流式响应
            llmManager.chatStreamBySentence(device, text,
                    (sentence, isStart, isEnd) -> {
                        processSentenceFromLlm(
                            session, 
                            sessionId, 
                            sentence, 
                            isStart, 
                            isEnd, 
                            ttsConfig, 
                            device.getVoiceName()
                        );
                    });
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 中止当前对话
     * 
     * @param session WebSocket会话
     * @param reason  中止原因
     * @return 处理结果
     */
    public Mono<Void> abortDialogue(WebSocketSession session, String reason) {
        String sessionId = session.getId();
        logger.info("中止对话 - SessionId: {}, Reason: {}", sessionId, reason);

        // 关闭音频流
        sessionManager.closeAudioSink(sessionId);
        sessionManager.setStreamingState(sessionId, false);
        
        // 清空待处理句子列表
        List<PendingSentence> pendingSentences = sessionPendingSentences.get(sessionId);
        if (pendingSentences != null) {
            synchronized (pendingSentences) {
                pendingSentences.clear();
            }
        }
        
        // 取消所有未完成的音频任务
        List<CompletableFuture<String>> audioTasks = sessionAudioTasks.get(sessionId);
        if (audioTasks != null) {
            synchronized (audioTasks) {
                for (CompletableFuture<String> task : audioTasks) {
                    if (!task.isDone()) {
                        task.cancel(true);
                    }
                }
                audioTasks.clear();
            }
        }

        // 终止语音发送
        return audioService.sendStop(session);
    }

    /**
     * 清理会话资源
     * 
     * @param sessionId 会话ID
     */
    public void cleanupSession(String sessionId) {
        sessionSentenceCounters.remove(sessionId);
        sessionSttStartTimes.remove(sessionId);
        sessionLlmStartTimes.remove(sessionId);
        sessionFullResponses.remove(sessionId);
        
        // 清理音频任务列表
        List<CompletableFuture<String>> audioTasks = sessionAudioTasks.remove(sessionId);
        if (audioTasks != null) {
            for (CompletableFuture<String> task : audioTasks) {
                if (!task.isDone()) {
                    task.cancel(true);
                }
            }
        }

        // 清理待处理句子列表
        sessionPendingSentences.remove(sessionId);
    }
}
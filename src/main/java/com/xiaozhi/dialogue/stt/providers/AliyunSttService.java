package com.xiaozhi.dialogue.stt.providers;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.translation.TranslationRecognizerParam;
import com.alibaba.dashscope.audio.asr.translation.TranslationRecognizerRealtime;
import com.alibaba.dashscope.audio.asr.translation.results.TranslationRecognizerResult;
import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConfig;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeModality;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.alibaba.dashscope.audio.omni.OmniRealtimeTranscriptionParam;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonObject;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

public class AliyunSttService implements SttService {
    private static final Logger logger = LoggerFactory.getLogger(AliyunSttService.class);
    private static final String PROVIDER_NAME = "aliyun";

    private final String apiKey;
    private final String model;
    public AliyunSttService(SysConfig config) {
        this.apiKey = config.getApiKey();
        this.model = config.getConfigName();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String recognition(byte[] audioData) {
        // 单次识别暂未实现，可以根据需要添加
        logger.warn("阿里云单次识别未实现，请使用流式识别");
        return null;
    }

    @Override
    public String streamRecognition(Sinks.Many<byte[]> audioSink) {
        try {
            if (model.toLowerCase().contains("gummy")) {
                return streamRecognitionGummy(audioSink);
            } else if (model.toLowerCase().contains("qwen")) {
                return streamRecognitionQwen(audioSink);
            } else {
                // paraformer 逻辑
                String actualModel = model;
                // 兼容以前的数据，如果不包含已知模型类型，则使用默认模型
                if (!model.toLowerCase().contains("paraformer") 
                    && !model.toLowerCase().contains("fun-asr")) {
                    actualModel = "paraformer-realtime-v2";
                    logger.info("未识别的模型类型: {}，使用默认模型: {}", model, actualModel);
                }
                return streamRecognitionParaformer(audioSink, actualModel);
            }
        } catch (Exception e) {
            logger.error("使用{}模型语音识别失败：", model, e);
            return "";
        }
    }

    /**
     * Paraformer 模型的流式识别
     */
    private String streamRecognitionParaformer(Sinks.Many<byte[]> audioSink, String modelName) {
        var recognizer = new Recognition();

        // 创建识别参数
        var param = RecognitionParam.builder()
                .model(modelName)
                .format("pcm")
                .sampleRate(AudioUtils.SAMPLE_RATE) // 使用16000Hz采样率
                .apiKey(apiKey)
                .build();

        // 使用 Reactor 执行流式识别
        var recognition = Flux.<String>create(sink -> {
            try {
                recognizer.streamCall(param, Flowable.create(emitter -> {
                            audioSink.asFlux().subscribe(
                                    chunk -> emitter.onNext(ByteBuffer.wrap(chunk)),
                                    emitter::onError,
                                    emitter::onComplete
                            );
                        }, BackpressureStrategy.BUFFER))
                        .timeout(90, TimeUnit.SECONDS)
                        .subscribe(result -> {
                                    if (result.isSentenceEnd()) {
                                        logger.info("语音识别结果({}): {}", modelName, result.getSentence().getText());
                                        sink.next(result.getSentence().getText());
                                    }
                                },
                                Throwable::printStackTrace,
                                sink::complete
                        );
            } catch (Exception e) {
                sink.error(e);
                logger.info("使用{}模型语音识别失败：", modelName, e);
            }
        });

        return recognition.reduce(new StringBuffer(), StringBuffer::append)
                .blockOptional()
                .map(StringBuffer::toString)
                .orElse("");
    }

    /**
     * Gummy 模型的流式识别（支持实时翻译）
     */
    private String streamRecognitionGummy(Sinks.Many<byte[]> audioSink) {
        StringBuilder result = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean hasError = new AtomicBoolean(false);
        
        // 初始化请求参数
        var param = TranslationRecognizerParam.builder()
                .apiKey(apiKey)
                .model(model)
                .format("pcm")
                .sampleRate(AudioUtils.SAMPLE_RATE)
                .transcriptionEnabled(true)
                .sourceLanguage("auto")
                .build();
        
        // 初始化回调接口
        ResultCallback<TranslationRecognizerResult> callback = 
                new ResultCallback<TranslationRecognizerResult>() {
                    @Override
                    public void onEvent(TranslationRecognizerResult recognizerResult) {
                        try {
                            
                            // 处理识别结果
                            if (recognizerResult.getTranscriptionResult() != null) {
                                if (recognizerResult.isSentenceEnd()) {
                                    String text = recognizerResult.getTranscriptionResult().getText();
                                    logger.info("语音识别结果({}): {}", model, text);
                                    synchronized (result) {
                                        result.append(text);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.error("处理识别结果时发生错误", e);
                        }
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        logger.error("语音识别错误({}): {}", model, e.getMessage(), e);
                        hasError.set(true);
                        latch.countDown();
                    }
                };
        
        // 初始化流式识别服务
        TranslationRecognizerRealtime translator = new TranslationRecognizerRealtime();
        
        try {
            // 启动流式语音识别
            translator.call(param, callback);
            
            // 订阅音频流并发送数据
            audioSink.asFlux().subscribe(
                    audioChunk -> {
                        try {
                            ByteBuffer buffer = ByteBuffer.wrap(audioChunk);
                            translator.sendAudioFrame(buffer);
                        } catch (Exception e) {
                            logger.error("发送音频数据时发生错误", e);
                        }
                    },
                    error -> {
                        logger.error("音频流错误", error);
                        translator.stop();
                        latch.countDown();
                    },
                    () -> {
                        translator.stop();
                    }
            );
            
            // 等待识别完成，最多90秒
            boolean completed = latch.await(90, TimeUnit.SECONDS);
            
            if (!completed) {
                logger.warn("语音识别超时({})", model);
            }
            
        } catch (Exception e) {
            logger.error("流式识别过程中发生错误({})", model, e);
            hasError.set(true);
        } finally {
            // 关闭 websocket 连接
            try {
                translator.getDuplexApi().close(1000, "bye");
            } catch (Exception e) {
                logger.error("关闭连接时发生错误", e);
            }
        }
        
        if (hasError.get()) {
            return "";
        }
        
        return result.toString();
    }

    /**
     * Qwen 模型的流式识别（qwen3-asr-flash-realtime）
     */
    private String streamRecognitionQwen(Sinks.Many<byte[]> audioSink) {
        StringBuilder result = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        AtomicReference<OmniRealtimeConversation> conversationRef = new AtomicReference<>(null);
        
        // 初始化请求参数
        OmniRealtimeParam param = OmniRealtimeParam.builder()
                .model(model)
                .url("wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
                .apikey(apiKey)
                .build();
        
        try {
            // 初始化回调接口
            OmniRealtimeConversation conversation = new OmniRealtimeConversation(param, new OmniRealtimeCallback() {
                @Override
                public void onOpen() {
                }

                @Override
                public void onEvent(JsonObject message) {
                    String type = message.get("type").getAsString();
                    switch(type) {
                        case "session.created":
                            break;
                        case "conversation.item.input_audio_transcription.completed":
                            String transcript = message.get("transcript").getAsString();
                            logger.info("语音识别结果({}): {}", model, transcript);
                            synchronized (result) {
                                result.append(transcript);
                            }
                            // 收到识别结果后关闭连接
                            if (conversationRef.get() != null && !isCompleted.get()) {
                                try {
                                    conversationRef.get().close(1000, "transcription_completed");
                                } catch (Exception e) {
                                    logger.error("关闭连接时发生错误", e);
                                    // 如果关闭失败，手动触发完成
                                    if (isCompleted.compareAndSet(false, true)) {
                                        latch.countDown();
                                    }
                                }
                            }
                            break;
                        case "input_audio_buffer.speech_started":
                            break;
                        case "input_audio_buffer.speech_stopped":
                            break;
                        case "response.done":
                            if (isCompleted.compareAndSet(false, true)) {
                                latch.countDown();
                            }
                            break;
                        default:
                            break;
                    }
                }

                @Override
                public void onClose(int code, String reason) {
                    logger.info("Qwen 语音识别连接关闭 - code: {}, reason: {}", code, reason);
                    if (isCompleted.compareAndSet(false, true)) {
                        latch.countDown();
                    }
                }
            });
            
            conversationRef.set(conversation);
            
            // 建立连接
            try {
                conversation.connect();
            } catch (NoApiKeyException e) {
                logger.error("API Key 无效", e);
                hasError.set(true);
                return "";
            }
            
            // 配置转录参数
            OmniRealtimeTranscriptionParam transcriptionParam = new OmniRealtimeTranscriptionParam();
            // transcriptionParam.setLanguage("zh");
            transcriptionParam.setInputAudioFormat("pcm");
            transcriptionParam.setInputSampleRate(AudioUtils.SAMPLE_RATE);
            
            // 配置会话参数
            OmniRealtimeConfig config = OmniRealtimeConfig.builder()
                    .modalities(Collections.singletonList(OmniRealtimeModality.TEXT))
                    .transcriptionConfig(transcriptionParam)
                    .enableTurnDetection(false)  // 关闭服务端VAD
                    .build();
            
            conversation.updateSession(config);
            
            // 订阅音频流并发送数据
            audioSink.asFlux().subscribe(
                    audioChunk -> {
                        try {
                            // 将音频数据转换为 Base64
                            String audioB64 = Base64.getEncoder().encodeToString(audioChunk);
                            conversation.appendAudio(audioB64);
                        } catch (Exception e) {
                            logger.error("发送音频数据时发生错误", e);
                        }
                    },
                    error -> {
                        logger.error("音频流错误", error);
                        conversation.close(1000, "error");
                        if (isCompleted.compareAndSet(false, true)) {
                            latch.countDown();
                        }
                    },
                    () -> {
                        // 本地VAD检测到语音结束（SPEECH_END）时会触发此回调
                        // 由于关闭了服务端VAD，需要手动调用 commit() 触发识别
                        if (!isCompleted.get()) {
                            // 手动提交识别请求（关闭服务端VAD后必须手动commit）
                            conversation.commit();
                        }
                    }
            );
            
            // 等待识别完成，最多90秒
            boolean completed = latch.await(90, TimeUnit.SECONDS);
            
            if (!completed) {
                logger.warn("语音识别超时({})", model);
                // 超时情况下主动关闭连接
                try {
                    conversation.close(1000, "timeout");
                } catch (Exception e) {
                    logger.error("关闭连接时发生错误", e);
                }
            }
            
        } catch (Exception e) {
            logger.error("流式识别过程中发生错误({})", model, e);
            hasError.set(true);
            // 发生异常时尝试关闭连接
            try {
                if (conversationRef.get() != null) {
                    conversationRef.get().close(1000, "error");
                }
            } catch (Exception ex) {
                logger.error("关闭连接时发生错误", ex);
            }
        }
        
        if (hasError.get()) {
            return "";
        }
        
        return result.toString();
    }
}
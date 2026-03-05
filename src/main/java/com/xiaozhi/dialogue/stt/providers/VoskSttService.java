package com.xiaozhi.dialogue.stt.providers;

import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.utils.AudioUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vosk STT 服务实现
 * 使用平台线程池实现异步处理，避免虚拟线程与 JNI native 内存绑定冲突
 */
public class VoskSttService implements SttService {

    private static final Logger logger = LoggerFactory.getLogger(VoskSttService.class);
    private static final String PROVIDER_NAME = "vosk";
    private static final int AUDIO_BUFFER_SIZE = 4096;
    private static final long STREAM_TIMEOUT_SECONDS = 90;
    private static final int QUEUE_POLL_TIMEOUT_MS = 100;

    // 使用平台线程池执行 JNI native 识别任务，避免虚拟线程与 native 内存绑定冲突
    private static final ExecutorService recognizerExecutor =
            Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    runnable -> {
                        Thread thread = new Thread(runnable, "vosk-recognizer-" + runnable.hashCode());
                        thread.setDaemon(true);
                        return thread;
                    }
            );

    // Vosk 模型相关对象
    private Model model;
    private String voskModelPath;
    private volatile boolean modelLoaded = false;

    /**
     * 初始化 Vosk 模型
     *
     * @throws Exception 如果模型加载失败
     */
    @PostConstruct
    public void initialize() throws Exception {
        try {
            // 检查是否是 macOS 操作系统
            String osName = System.getProperty("os.name").toLowerCase();
            // 检查是否是 ARM 架构（用于 M 系列芯片）
            String osArch = System.getProperty("os.arch").toLowerCase();

            if (osName.contains("mac") && osArch.contains("aarch64")) {
                // 如果是 macOS 并且是 ARM 架构（M 系列芯片）
                String libPath = System.getProperty("user.dir") + "/lib/libvosk.dylib";
                if (Files.exists(Paths.get(libPath))) {
                    System.load(libPath);
                    logger.info("Vosk library loaded for macOS M-series chip: {}", libPath);
                } else {
                    logger.warn("Vosk library not found at: {}", libPath);
                }
            } else {
                logger.info("Not macOS M-series chip, skipping Vosk library load.");
            }

            // 禁用 Vosk 日志输出
            LibVosk.setLogLevel(LogLevel.WARNINGS);

            // 加载模型，路径为配置的模型目录
            voskModelPath = System.getProperty("user.dir") + File.separator +
                    Paths.get("models", "vosk-model");

            // 验证模型路径是否存在
            if (!Files.exists(Paths.get(voskModelPath))) {
                throw new IOException("Vosk model directory not found: " + voskModelPath);
            }

            model = new Model(voskModelPath);
            modelLoaded = true;
            logger.info("Vosk 模型加载成功！路径：{}", voskModelPath);

        } catch (Exception e) {
            modelLoaded = false;
            logger.error("Vosk 模型加载失败！将使用其他 STT 服务：{}", e.getMessage(), e);
            throw new Exception("Vosk model loading failed: " + e.getMessage(), e);
        }
    }

    /**
     * 释放资源
     */
    @PreDestroy
    public void destroy() {
        if (model != null) {
            try {
                model.close();
                logger.info("Vosk 模型已关闭");
            } catch (Exception e) {
                logger.warn("关闭 Vosk 模型时发生错误：{}", e.getMessage());
            }
        }

        if (!recognizerExecutor.isShutdown()) {
            recognizerExecutor.shutdown();
            try {
                if (!recognizerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    recognizerExecutor.shutdownNow();
                }
                logger.info("Vosk 识别线程池已关闭");
            } catch (InterruptedException e) {
                recognizerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 检查模型是否成功加载
     *
     * @return 如果模型加载成功返回 true，否则返回 false
     */
    public boolean isModelLoaded() {
        return modelLoaded && model != null;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String recognition(byte[] audioData) {
        if (!isModelLoaded()) {
            logger.error("Vosk 模型未加载，无法进行识别！");
            return null;
        }

        if (audioData == null || audioData.length == 0) {
            logger.warn("音频数据为空！");
            return null;
        }

        // 将原始音频数据转换为 WAV 格式并保存（用于调试）
        String fileName = AudioUtils.saveAsWav(audioData);
        logger.debug("音频文件已保存：{}", fileName);

        try (Recognizer recognizer = new Recognizer(model, AudioUtils.SAMPLE_RATE)) {
            return processAudioStream(recognizer, audioData);
        } catch (Exception e) {
            logger.error("处理音频时发生错误！", e);
            return null;
        }
    }

    /**
     * 处理音频流进行识别
     *
     * @param recognizer Vosk 识别器
     * @param audioData 音频数据
     * @return 识别结果文本
     */
    private String processAudioStream(Recognizer recognizer, byte[] audioData) throws IOException {
        ByteArrayInputStream audioStream = new ByteArrayInputStream(audioData);
        byte[] buffer = new byte[AUDIO_BUFFER_SIZE];
        int bytesRead;

        while ((bytesRead = audioStream.read(buffer)) != -1) {
            if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                // 如果识别到完整的结果
                String result = recognizer.getResult();
                return extractTextFromResult(result);
            }
        }

        // 返回最终的识别结果
        String finalResult = recognizer.getFinalResult();
        return extractTextFromResult(finalResult);
    }

    /**
     * 从 JSON 结果中提取文本
     *
     * @param result JSON 格式的结果字符串
     * @return 提取的文本，如果为空则返回空字符串
     */
    private String extractTextFromResult(String result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        try {
            JSONObject jsonResult = new JSONObject(result);
            String text = jsonResult.optString("text", "");
            return text.replaceAll("\\s+", "");
        } catch (Exception e) {
            logger.warn("解析 Vosk 结果失败：{}", e.getMessage());
            return "";
        }
    }

    @Override
    public boolean supportsStreaming() {
        return isModelLoaded();
    }

    @Override
    public String streamRecognition(Flux<byte[]> audioSink) {
        if (!isModelLoaded()) {
            logger.error("Vosk 模型未加载，无法进行流式识别！");
            return null;
        }

        if (audioSink == null) {
            logger.warn("音频流为空！");
            return null;
        }

        // 使用阻塞队列存储音频数据
        BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        List<String> recognizedText = new ArrayList<>();

        // 订阅 Sink 并将数据放入队列
        audioSink.subscribe(
                data -> {
                    if (data != null && data.length > 0) {
                        audioQueue.offer(data);
                    }
                },
                error -> {
                    logger.error("音频流处理错误：{}", error.getMessage());
                    isCompleted.set(true);
                },
                () -> isCompleted.set(true)
        );

        // 使用平台线程池执行识别任务，避免虚拟线程与 JNI native 内存绑定冲突
        Future<String> future = recognizerExecutor.submit(() ->
                processStreamingRecognition(audioQueue, isCompleted, recognizedText)
        );

        try {
            return future.get(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warn("Vosk 识别超时（{}秒）", STREAM_TIMEOUT_SECONDS, e);
            future.cancel(true);
            return String.join("", recognizedText);
        } catch (InterruptedException e) {
            logger.warn("等待 Vosk 识别完成时被中断", e);
            Thread.currentThread().interrupt();
            future.cancel(true);
            return String.join("", recognizedText);
        } catch (Exception e) {
            logger.error("Vosk 识别任务执行失败", e);
            future.cancel(true);
            return String.join("", recognizedText);
        }
    }

    /**
     * 处理流式识别
     *
     * @param audioQueue 音频数据队列
     * @param isCompleted 是否已完成
     * @param recognizedText 已识别的文本列表
     * @return 最终识别结果
     */
    private String processStreamingRecognition(
            BlockingQueue<byte[]> audioQueue,
            AtomicBoolean isCompleted,
            List<String> recognizedText) {

        try (Recognizer recognizer = new Recognizer(model, AudioUtils.SAMPLE_RATE)) {
            while (!isCompleted.get() || !audioQueue.isEmpty()) {
                try {
                    byte[] audioChunk = audioQueue.poll(QUEUE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (audioChunk != null) {
                        processAudioChunk(recognizer, audioChunk, recognizedText);
                    }

                    // 如果已完成且队列为空，获取最终结果
                    if (isCompleted.get() && audioQueue.isEmpty()) {
                        processFinalResult(recognizer, recognizedText);
                        break;
                    }
                } catch (InterruptedException e) {
                    logger.warn("音频数据队列等待被中断", e);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Vosk 流式识别过程中发生错误", e);
        }

        return String.join("", recognizedText);
    }

    /**
     * 处理单个音频块
     *
     * @param recognizer Vosk 识别器
     * @param audioChunk 音频数据块
     * @param recognizedText 已识别的文本列表
     */
    private void processAudioChunk(
            Recognizer recognizer,
            byte[] audioChunk,
            List<String> recognizedText) {

        boolean hasResult = recognizer.acceptWaveForm(audioChunk, audioChunk.length);
        if (hasResult) {
            // 提取部分识别结果中的文本
            String result = recognizer.getResult();
            String text = extractTextFromResult(result);
            if (!text.isEmpty()) {
                recognizedText.add(text);
                logger.debug("Vosk 识别中间结果：{}", text);
            }
        }
    }

    /**
     * 处理最终识别结果
     *
     * @param recognizer Vosk 识别器
     * @param recognizedText 已识别的文本列表
     */
    private void processFinalResult(Recognizer recognizer, List<String> recognizedText) {
        String finalText = recognizer.getFinalResult();
        String text = extractTextFromResult(finalText);
        if (!text.isEmpty()) {
            recognizedText.add(text);
            logger.debug("Vosk 识别最终结果：{}", text);
        }
    }
}

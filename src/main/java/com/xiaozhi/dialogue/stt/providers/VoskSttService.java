package com.xiaozhi.dialogue.stt.providers;

import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.utils.AudioUtils;
import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import reactor.core.publisher.Sinks;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vosk STT服务实现
 * 使用JDK 21虚拟线程实现异步处理
 */
public class VoskSttService implements SttService {

    private static final Logger logger = LoggerFactory.getLogger(VoskSttService.class);
    private static final String PROVIDER_NAME = "vosk";
    private static final int QUEUE_TIMEOUT_MS = 5000; // 队列等待超时时间

    // Vosk模型相关对象
    private Model model;
    private String voskModelPath;
    private boolean modelLoaded = false;

    /**
     * 初始化Vosk模型
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
                System.load(System.getProperty("user.dir") + "/lib/libvosk.dylib");
                logger.info("Vosk library loaded for macOS M-series chip.");
            } else {
                logger.info("Not macOS M-series chip, skipping Vosk library load.");
            }
            // 禁用Vosk日志输出
            LibVosk.setLogLevel(LogLevel.WARNINGS);

            // 加载模型，路径为配置的模型目录
            voskModelPath = System.getProperty("user.dir") + File.separator + Paths.get("models", "vosk-model");
            model = new Model(voskModelPath);
            modelLoaded = true;
            logger.info("Vosk 模型加载成功！路径: {}", voskModelPath);
        } catch (Exception e) {
            modelLoaded = false;
            logger.warn("Vosk 模型加载失败！将使用其他STT服务: {}", e.getMessage());
            throw new Exception("Vosk model loading failed: " + e.getMessage(), e);
        }
    }

    /**
     * 检查模型是否成功加载
     *
     * @return 如果模型加载成功返回true，否则返回false
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
            logger.error("Vosk模型未加载，无法进行识别！");
            return null;
        }

        if (audioData == null || audioData.length == 0) {
            logger.warn("音频数据为空！");
            return null;
        }

        // 将原始音频数据转换为WAV格式并保存
        String fileName = AudioUtils.saveAsWav(audioData);

        try (Recognizer recognizer = new Recognizer(model, AudioUtils.SAMPLE_RATE)) {
            ByteArrayInputStream audioStream = new ByteArrayInputStream(audioData);

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = audioStream.read(buffer)) != -1) {
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    // 如果识别到完整的结果
                    String result = recognizer.getResult();
                    JSONObject jsonResult = new JSONObject(result);
                    return jsonResult.getString("text").replaceAll("\\s+", "");
                }
            }

            // 返回最终的识别结果
            String finalResult = recognizer.getFinalResult();
            JSONObject jsonFinal = new JSONObject(finalResult);
            return jsonFinal.getString("text").replaceAll("\\s+", "");

        } catch (Exception e) {
            logger.error("处理音频时发生错误！", e);
            return null;
        }
    }

    @Override
    public boolean supportsStreaming() {
        return isModelLoaded();
    }

    @Override
    public String streamRecognition(Sinks.Many<byte[]> audioSink) {
        if (!isModelLoaded()) {
            logger.error("Vosk模型未加载，无法进行流式识别！");
            return null;
        }

        // 使用阻塞队列存储音频数据
        BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        List<String> recognizedText = new ArrayList<>();
        StringBuilder finalResult = new StringBuilder();

        // 订阅Sink并将数据放入队列
        audioSink.asFlux().subscribe(
                data -> audioQueue.offer(data),
                error -> {
                    logger.error("音频流处理错误", error);
                    isCompleted.set(true);
                },
                () -> isCompleted.set(true)
        );

        // 使用虚拟线程处理音频识别
        try {
            Thread virtualThread = Thread.startVirtualThread(() -> {
                try (Recognizer recognizer = new Recognizer(model, AudioUtils.SAMPLE_RATE)) {
                    while (!isCompleted.get() || !audioQueue.isEmpty()) {
                        try {
                            byte[] audioChunk = audioQueue.poll(100, TimeUnit.MILLISECONDS);
                            if (audioChunk != null) {
                                boolean hasResult = recognizer.acceptWaveForm(audioChunk, audioChunk.length);
                                if (hasResult) {
                                    // 提取部分识别结果中的文本
                                    String result = recognizer.getResult();
                                    JSONObject jsonResult = new JSONObject(result);
                                    if (jsonResult.has("text") && !jsonResult.getString("text").isEmpty()) {
                                        String text = jsonResult.getString("text").replaceAll("\\s+", "");
                                        recognizedText.add(text);
                                        logger.debug("Vosk识别中间结果: {}", text);
                                    }
                                }
                            }

                            // 如果已完成且队列为空，获取最终结果
                            if (isCompleted.get() && audioQueue.isEmpty()) {
                                String finalText = recognizer.getFinalResult();
                                JSONObject jsonFinal = new JSONObject(finalText);
                                if (jsonFinal.has("text")) {
                                    String text = jsonFinal.getString("text").replaceAll("\\s+", "");
                                    if (!text.isEmpty()) {
                                        recognizedText.add(text);
                                        logger.debug("Vosk识别最终结果: {}", text);
                                    }
                                }
                                break;
                            }
                        } catch (InterruptedException e) {
                            logger.warn("音频数据队列等待被中断", e);
                            Thread.currentThread().interrupt(); // 重新设置中断标志
                            break;
                        }
                    }

                    // 合并所有识别结果
                    for (String text : recognizedText) {
                        finalResult.append(text);
                    }

                } catch (Exception e) {
                    logger.error("Vosk流式识别过程中发生错误", e);
                }
            });
            
            // 等待虚拟线程完成，最多等待90秒
            try {
                virtualThread.join(90000); // 90秒超时
                if (virtualThread.isAlive()) {
                    virtualThread.interrupt(); // 中断线程
                }
            } catch (InterruptedException e) {
                logger.warn("等待Vosk识别完成时被中断", e);
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            logger.error("启动虚拟线程失败", e);
        }

        return finalResult.toString();
    }
}

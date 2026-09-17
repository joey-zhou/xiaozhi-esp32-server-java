package com.xiaozhi.ai.stt.providers;

import com.xiaozhi.common.annotation.MonitoredOperation;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.utils.AudioUtils;
import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
/**
 * Vosk STT服务实现
 * 使用JDK 21虚拟线程实现异步处理
 */
@Slf4j
public class VoskSttService implements SttService {

    private static final String PROVIDER_NAME = "vosk";
    private static final int QUEUE_TIMEOUT_MS = 100; // 队列等待超时时间
    // 上游未终结音频流时的兜底上限，需远大于设备上行抖动，否则弱网会截断用户没说完的话
    private static final long IDLE_TIMEOUT_MS = 5000;

    // 必须是平台线程，虚拟线程与 Recognizer 的 JNI native 内存绑定冲突。
    // 任务体是阻塞轮询而非 CPU 计算，故上限取核数*4；SynchronousQueue 不排队，扩不出线程即拒绝。
    private static final int CORE_RECOGNIZER_THREADS = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService recognizerExecutor = new ThreadPoolExecutor(
            CORE_RECOGNIZER_THREADS, CORE_RECOGNIZER_THREADS * 4,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "vosk-stt-worker");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

    static {
        // 注册JVM关闭钩子，确保线程池被正确关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            recognizerExecutor.shutdown();
            try {
                if (!recognizerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    recognizerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                recognizerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }, "vosk-stt-shutdown"));
    }

    // Vosk模型相关对象
    private Model model;
    private String voskModelPath;
    private boolean modelLoaded = false;
    private final String nativeLibDir;

    public VoskSttService(String nativeLibDir, String voskModelDir) {
        this.nativeLibDir = nativeLibDir;
        this.voskModelPath = voskModelDir;
    }

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
                Path libPath = Path.of(nativeLibDir).toAbsolutePath().normalize().resolve("libvosk.dylib");
                System.load(libPath.toString());
                log.info("Vosk library loaded for macOS M-series chip.");
            } else {
                log.info("Not macOS M-series chip, skipping Vosk library load.");
            }
            // 禁用Vosk日志输出
            LibVosk.setLogLevel(LogLevel.WARNINGS);

            // 加载模型，路径为配置的模型目录
            voskModelPath = Path.of(voskModelPath).toAbsolutePath().normalize().toString();
            if (!Files.isDirectory(Path.of(voskModelPath))) {
                throw new Exception("Vosk model directory not found: " + voskModelPath);
            }
            model = new Model(voskModelPath);
            modelLoaded = true;
            log.info("Vosk 模型加载成功！路径: {}", voskModelPath);
        } catch (Exception e) {
            modelLoaded = false;
            log.warn("Vosk 模型加载失败！将使用其他STT服务: {}", e.getMessage());
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

    /**
     * 安全地把中间识别结果通知给上层：空文本不回调，回调异常不影响识别主流程。
     */
    private void notifyPartial(Consumer<String> onPartialText, String text) {
        if (onPartialText == null || text == null || text.isEmpty()) {
            return;
        }
        try {
            onPartialText.accept(text);
        } catch (Exception e) {
            log.debug("中间识别结果回调异常，已忽略", e);
        }
    }

    @MonitoredOperation(name = "xiaozhi.stt.stream")
    @Override
    public SttResult stream(Flux<byte[]> audioSink) {
        return stream(audioSink, text -> {
        });
    }

    @MonitoredOperation(name = "xiaozhi.stt.stream")
    @Override
    public SttResult stream(Flux<byte[]> audioSink, Consumer<String> onPartialText) {
        if (!isModelLoaded()) {
            log.error("Vosk模型未加载，无法进行流式识别！");
            return null;
        }

        // 使用阻塞队列存储音频数据
        BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        List<String> recognizedText = new ArrayList<>();
        StringBuilder finalResult = new StringBuilder();
        // 识别失败原因短码，成功为 null
        AtomicReference<String> failureReason = new AtomicReference<>();

        // 订阅Sink并将数据放入队列
        Disposable audioSubscription = audioSink.subscribe(
                data -> audioQueue.offer(data),
                error -> {
                    log.error("音频流处理错误", error);
                    isCompleted.set(true);
                },
                () -> isCompleted.set(true)
        );

        // 使用平台线程池执行识别任务，避免虚拟线程与 JNI native 内存绑定冲突
        Future<?> future;
        try {
            future = recognizerExecutor.submit(() -> {
                try (Recognizer recognizer = new Recognizer(model, AudioUtils.SAMPLE_RATE)) {
                    // 已通知过的实时中间文本，避免同一段文本被反复回调
                    String lastPartial = "";
                    long idleMs = 0;
                    while (!isCompleted.get() || !audioQueue.isEmpty()) {
                        try {
                            byte[] audioChunk = audioQueue.poll(QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                            if (audioChunk != null) {
                                idleMs = 0;
                                boolean hasResult = recognizer.acceptWaveForm(audioChunk, audioChunk.length);
                                if (hasResult) {
                                    // 提取部分识别结果中的文本
                                    String result = recognizer.getResult();
                                    JSONObject jsonResult = new JSONObject(result);
                                    if (jsonResult.has("text") && !jsonResult.getString("text").isEmpty()) {
                                        String text = jsonResult.getString("text").replaceAll("\\s+", "");
                                        recognizedText.add(text);
                                        log.debug("Vosk识别中间结果: {}", text);
                                        notifyPartial(onPartialText, text);
                                    }
                                } else {
                                    // 未触发端点时，Vosk 只能通过 getPartialResult 拿到实时中间文本。
                                    // 该调用是纯读取，不改变识别器状态，也不影响最终结果。
                                    try {
                                        JSONObject jsonPartial = new JSONObject(recognizer.getPartialResult());
                                        String partialText = jsonPartial.optString("partial", "")
                                                .replaceAll("\\s+", "");
                                        if (!partialText.isEmpty() && !partialText.equals(lastPartial)) {
                                            lastPartial = partialText;
                                            notifyPartial(onPartialText, partialText);
                                        }
                                    } catch (Exception e) {
                                        log.debug("Vosk中间结果解析失败，已忽略", e);
                                    }
                                }
                            } else {
                                idleMs += QUEUE_TIMEOUT_MS;
                            }

                            // 已完成且队列为空时收尾；空闲超限是上游未终结音频流时的兜底
                            if ((isCompleted.get() && audioQueue.isEmpty()) || idleMs >= IDLE_TIMEOUT_MS) {
                                if (idleMs >= IDLE_TIMEOUT_MS) {
                                    log.warn("音频流长时间无数据，主动结束识别");
                                }
                                String finalText = recognizer.getFinalResult();
                                JSONObject jsonFinal = new JSONObject(finalText);
                                if (jsonFinal.has("text")) {
                                    String text = jsonFinal.getString("text").replaceAll("\\s+", "");
                                    if (!text.isEmpty()) {
                                        recognizedText.add(text);
                                        log.debug("Vosk识别最终结果: {}", text);
                                    }
                                }
                                break;
                            }
                        } catch (InterruptedException e) {
                            log.warn("音频数据队列等待被中断", e);
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    // 合并所有识别结果
                    for (String text : recognizedText) {
                        finalResult.append(text);
                    }

                } catch (Exception e) {
                    log.error("Vosk流式识别过程中发生错误", e);
                    failureReason.set(SttResult.FAILURE_LOCAL_ERROR);
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("Vosk识别线程池已满，拒绝本次识别任务", e);
            // 无人消费队列，必须退订上游，否则音频会一直堆进 audioQueue
            audioSubscription.dispose();
            audioQueue.clear();
            return SttResult.failure(SttResult.FAILURE_LOCAL_ERROR);
        }

        try {
            future.get(90, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("等待Vosk识别完成时被中断", e);
            Thread.currentThread().interrupt();
            future.cancel(true);
        } catch (Exception e) {
            log.error("Vosk识别任务执行失败", e);
            failureReason.set(SttResult.FAILURE_LOCAL_ERROR);
            future.cancel(true);
        }

        return SttResult.textOnly(finalResult.toString()).withFailure(failureReason.get());
    }
}

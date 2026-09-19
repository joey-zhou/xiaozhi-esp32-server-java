package com.xiaozhi.ai.stt.providers;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.common.monitoring.CountingRejectionHandler;
import com.xiaozhi.utils.AudioUtils;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 基于 sherpa-onnx 的本地语音识别，模型为 SenseVoice-Small（中英日韩粤，自带情绪与事件标签）。
 * <p>
 * 整句离线识别：先收齐本轮音频再一次解码，因此没有中间文本，ASR 首字打断在这个 provider 上不会触发，
 * 打断退回 VAD 模式。解码是纯 CPU 计算，进一个有界的平台线程池，排队即准入，满了拒绝当次识别。
 * 识别器进程内长期持有，不调用 release()：与正在解码的线程并发释放会直接崩掉 JVM，换模型需重启进程。
 */
@Slf4j
public class SenseVoiceSttService implements SttService {

    private static final String PROVIDER_NAME = "sherpa-onnx";
    private static final int QUEUE_TIMEOUT_MS = 100;
    // 上游未终结音频流时的兜底上限，需远大于设备上行抖动，否则弱网会截断用户没说完的话
    private static final long IDLE_TIMEOUT_MS = 5000;
    // 从开口起算的单轮总时长上限，与其它 provider 口径一致；分段拼接每 60 秒换一条流，正常到不了这里
    private static final long RECOGNITION_TIMEOUT_MS = 90_000;
    private static final int PENDING_DECODES = 64;

    /** 解码这份 CPU 预算：同时占用的核数上限，余下的核留给本地合成与对话主链路 */
    private static final int DECODE_CORE_BUDGET = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    /** 模型加载前按默认线程数建池，加载时再按实际线程数收放 */
    private static final int DEFAULT_NUM_THREADS = 2;
    private static final CountingRejectionHandler REJECTION_HANDLER =
            new CountingRejectionHandler(new ThreadPoolExecutor.AbortPolicy());
    // 平台线程：解码在 JNI 里长时间占着 CPU，放虚拟线程会把载体线程钉死
    private static final ThreadPoolExecutor DECODE_EXECUTOR = new ThreadPoolExecutor(
            decodeConcurrency(DECODE_CORE_BUDGET, DEFAULT_NUM_THREADS),
            decodeConcurrency(DECODE_CORE_BUDGET, DEFAULT_NUM_THREADS),
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(PENDING_DECODES),
            r -> {
                Thread t = new Thread(r, "sherpa-stt-worker");
                t.setDaemon(true);
                return t;
            },
            REJECTION_HANDLER);

    private final String modelDir;
    private final int numThreads;
    private volatile OfflineRecognizer recognizer;

    public SenseVoiceSttService(String modelDir, int numThreads) {
        this.modelDir = modelDir;
        this.numThreads = Math.max(1, numThreads);
        resizeDecodePool(decodeConcurrency(DECODE_CORE_BUDGET, this.numThreads));
    }

    /**
     * 同时解码的路数。每路解码占 numThreads 个核，路数 × numThreads 不得超过预算；
     * 识别是整句离线解码，耗时直接算进首响，所以保单路速度、让并发路数去迁就预算。
     */
    static int decodeConcurrency(int coreBudget, int numThreads) {
        return Math.max(1, coreBudget / Math.max(1, numThreads));
    }

    /** 收缩时先降 core 再降 max，扩张时反过来，否则 core 大于 max 会抛 IllegalArgumentException */
    private static synchronized void resizeDecodePool(int concurrency) {
        if (concurrency == DECODE_EXECUTOR.getMaximumPoolSize()) {
            return;
        }
        if (concurrency < DECODE_EXECUTOR.getMaximumPoolSize()) {
            DECODE_EXECUTOR.setCorePoolSize(concurrency);
            DECODE_EXECUTOR.setMaximumPoolSize(concurrency);
        } else {
            DECODE_EXECUTOR.setMaximumPoolSize(concurrency);
            DECODE_EXECUTOR.setCorePoolSize(concurrency);
        }
        log.info("SenseVoice 解码并发调整为 {} 路", concurrency);
    }

    /**
     * 加载模型。目录里优先取 int8 量化模型，体积小四倍、CPU 上更快，精度差别听不出来。
     */
    public void initialize() throws Exception {
        Path dir = Path.of(modelDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new Exception("SenseVoice model directory not found: " + dir);
        }
        String model = firstExisting(dir, "model.int8.onnx", "model.onnx");
        String tokens = firstExisting(dir, "tokens.txt");

        OfflineSenseVoiceModelConfig senseVoice = OfflineSenseVoiceModelConfig.builder()
                .setModel(model)
                .setLanguage("auto")
                .setInverseTextNormalization(true)
                .build();
        OfflineModelConfig modelConfig = OfflineModelConfig.builder()
                .setSenseVoice(senseVoice)
                .setTokens(tokens)
                .setNumThreads(numThreads)
                .setDebug(false)
                .setProvider("cpu")
                .build();
        OfflineRecognizerConfig config = OfflineRecognizerConfig.builder()
                .setOfflineModelConfig(modelConfig)
                .setDecodingMethod("greedy_search")
                .build();
        long start = System.currentTimeMillis();
        recognizer = new OfflineRecognizer(config);
        log.info("SenseVoice 模型加载成功，路径: {}, 线程数: {}, 解码并发: {} 路, 耗时: {}ms",
                dir, numThreads, DECODE_EXECUTOR.getMaximumPoolSize(), System.currentTimeMillis() - start);
    }

    public boolean isModelLoaded() {
        return recognizer != null;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public SttResult stream(Flux<byte[]> audioSink) {
        return stream(audioSink, null);
    }

    /** 整句识别没有中间文本，onPartialText 不会被调用 */
    @Override
    public SttResult stream(Flux<byte[]> audioSink, Consumer<String> onPartialText) {
        if (!isModelLoaded()) {
            log.error("SenseVoice 模型未加载，无法识别");
            return SttResult.failure(SttResult.FAILURE_LOCAL_ERROR);
        }
        long deadline = System.currentTimeMillis() + RECOGNITION_TIMEOUT_MS;

        BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        Disposable subscription = audioSink.subscribe(
                audioQueue::offer,
                error -> {
                    log.error("音频流处理错误", error);
                    completed.set(true);
                },
                () -> completed.set(true));

        // 收音留在调用线程上，这里只是等流结束，不占解码线程
        List<byte[]> frames = new ArrayList<>();
        boolean timedOut = false;
        try {
            long idleMs = 0;
            while (!(completed.get() && audioQueue.isEmpty())) {
                byte[] chunk = audioQueue.poll(QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (chunk != null) {
                    idleMs = 0;
                    frames.add(chunk);
                } else {
                    idleMs += QUEUE_TIMEOUT_MS;
                    if (idleMs >= IDLE_TIMEOUT_MS) {
                        log.warn("音频流长时间无数据，主动结束识别");
                        break;
                    }
                }
                if (System.currentTimeMillis() >= deadline) {
                    timedOut = true;
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            subscription.dispose();
            return SttResult.failure(SttResult.FAILURE_LOCAL_ERROR);
        } finally {
            subscription.dispose();
        }
        if (timedOut) {
            log.warn("SenseVoice 识别超过 {} 秒仍未收句", RECOGNITION_TIMEOUT_MS / 1000);
            return SttResult.failure(SttResult.FAILURE_TIMEOUT);
        }

        byte[] pcm = AudioUtils.joinPcmFrames(frames);
        if (pcm.length == 0) {
            return SttResult.textOnly("");
        }

        Future<SttResult> future;
        try {
            future = DECODE_EXECUTOR.submit(() -> decode(pcm));
        } catch (RejectedExecutionException e) {
            log.error("sherpa-onnx 识别线程池已满，拒绝本次识别任务");
            return SttResult.failure(SttResult.FAILURE_LOCAL_ERROR);
        }
        try {
            return future.get(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 与远端 provider 口径一致：超时不是本地错误，上层据此决定不重放
            log.warn("SenseVoice 解码超过单轮时长上限");
            future.cancel(true);
            return SttResult.failure(SttResult.FAILURE_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return SttResult.failure(SttResult.FAILURE_LOCAL_ERROR);
        } catch (Exception e) {
            log.error("SenseVoice 识别失败", e);
            return SttResult.failure(SttResult.FAILURE_LOCAL_ERROR);
        }
    }

    /**
     * 整句解码。sherpa 已把 SenseVoice 输出里的语种、情绪、事件标签拆到结果字段，文本是干净的。
     */
    SttResult decode(byte[] pcm) {
        long start = System.currentTimeMillis();
        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(AudioUtils.pcm16ToFloats(pcm), AudioUtils.SAMPLE_RATE);
            recognizer.decode(stream);
            OfflineRecognizerResult result = recognizer.getResult(stream);
            String text = result.getText() == null ? "" : result.getText().strip();
            String emotion = normalizeTag(result.getEmotion());
            long elapsed = System.currentTimeMillis() - start;
            double audioSeconds = pcm.length / (double) (AudioUtils.SAMPLE_RATE * 2);
            log.info("语音识别完成(sherpa-onnx): {} [情感: {}, 事件: {}, 音频: {}s, 耗时: {}ms, RTF: {}]",
                    text, emotion, normalizeTag(result.getEvent()), String.format("%.1f", audioSeconds), elapsed,
                    String.format("%.3f", audioSeconds > 0 ? elapsed / 1000.0 / audioSeconds : 0));
            return emotion == null ? SttResult.textOnly(text) : SttResult.withEmotion(text, emotion, null);
        } finally {
            stream.release();
        }
    }

    /**
     * SenseVoice 的标签形如 {@code <|HAPPY|>}、{@code <|Speech|>}，去掉包裹转小写，
     * 与火山、阿里云返回的 happy / neutral 这类取值对齐；空标签返回 null。
     */
    static String normalizeTag(String tag) {
        if (tag == null) {
            return null;
        }
        String value = tag.strip();
        if (value.startsWith("<|") && value.endsWith("|>")) {
            value = value.substring(2, value.length() - 2);
        }
        value = value.strip().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    private static String firstExisting(Path dir, String... names) throws Exception {
        for (String name : names) {
            Path file = dir.resolve(name);
            if (Files.isRegularFile(file)) {
                return file.toString();
            }
        }
        throw new Exception("SenseVoice model file not found in " + dir + ": " + String.join(" / ", names));
    }
}

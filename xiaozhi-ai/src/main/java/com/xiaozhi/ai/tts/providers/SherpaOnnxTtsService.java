package com.xiaozhi.ai.tts.providers;

import com.k2fsa.sherpa.onnx.*;
import com.xiaozhi.common.annotation.MonitoredOperation;
import com.xiaozhi.ai.tts.TtsOverloadException;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.XiaozhiTtsOptions;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.monitoring.CountingRejectionHandler;
import com.xiaozhi.utils.AudioUtils;

import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
/**
 * 基于 sherpa-onnx 的本地语音合成服务
 * 支持 VITS、Kokoro、Matcha 等多种本地 TTS 模型
 *
 * voiceName 格式：modelDir:modelType:speakerId
 *   示例：vits-melo-tts-zh_en:vits:0
 *         kokoro-multi-lang:kokoro:3
 *         matcha-zh-baker:matcha:0
 * <p>
 * 合成是纯 CPU 计算，全部经共享的合成线程池，并发数即同时占用的核数：单次合成的 onnxruntime
 * 线程数默认 1，于是「并发数 × 线程数」不超发就等于并发数。超发会让 RTF 线性恶化，一旦越过 1
 * 就合成慢于播放，句间必然出现停顿。排队超时或队列满即放弃本句，抛 {@link TtsOverloadException}。
 */
@Slf4j
public class SherpaOnnxTtsService implements TtsService {
    private static final String PROVIDER_NAME = "sherpa-onnx";

    // 缓存 OfflineTts 实例，避免重复加载模型（key = modelPath:modelType），进程内长期持有
    // 实例一旦放入就不再移除、不调用 release()。release() 会 delete native 指针，
    // 与正在执行的 generate 并发即 use-after-free，直接 SIGSEGV 崩掉整个 JVM
    // 实例只能经由本 Map 发布，由 ConcurrentHashMap 保证 native 指针对其他线程可见
    // 模型文件被替换后需重启进程才生效
    private static final Map<String, OfflineTts> ttsCache = new ConcurrentHashMap<>();

    // 并发数缺省值：本地 STT 解码占核数的一半、声纹提取占四分之一，合成只能拿剩下的
    private static final int DEFAULT_MAX_CONCURRENT = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
    // 单条许可对应的排队位数，乘出来的队列深度与 WAIT_TIMEOUT_MS 的等待上限量级一致
    private static final int QUEUE_PER_PERMIT = 8;
    // 排队等待上限缺省值，超过则本句已不可能在用户容忍时间内播出
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 8000;

    private static final CountingRejectionHandler REJECTION_HANDLER =
            new CountingRejectionHandler(new ThreadPoolExecutor.AbortPolicy());
    // 累计放弃的句子数，本版没有指标导出，只在测试里可见
    private static final AtomicLong droppedCount = new AtomicLong();
    private static volatile ThreadPoolExecutor synthExecutor;
    private static volatile long waitTimeoutMs = DEFAULT_WAIT_TIMEOUT_MS;
    // 单次合成的 onnxruntime 线程数，进程级配置，由 SherpaTtsConfig 启动时设定
    private static volatile int synthNumThreads = 1;

    private final XiaozhiTtsOptions options;
    private final String outputPath;

    // 模型目录路径
    private final String modelPath;
    // 模型类型：kokoro, vits, matcha
    private final String modelType;
    // Speaker ID
    private final int speakerId;

    public SherpaOnnxTtsService(
            ConfigBO config,
            String voiceName,
            Double pitch,
            Double speed,
            String outputPath,
            String ttsModelsDir) {
        this.options = XiaozhiTtsOptions.builder().voiceName(voiceName).pitch(pitch).speed(speed).build();
        this.outputPath = outputPath;

        // 解析 voiceName，格式：modelDir:modelType:speakerId
        // 如：vits-melo-tts-zh_en:vits:0、kokoro-multi-lang:kokoro:3
        String[] parts = voiceName != null ? voiceName.split(":") : new String[]{};
        if (parts.length != 3) {
            throw new IllegalArgumentException("voiceName 格式错误，期望 modelDir:modelType:speakerId，实际: " + voiceName);
        }
        this.modelPath = Path.of(ttsModelsDir).toAbsolutePath().normalize().resolve(parts[0]).toString();
        this.modelType = parts[1].toLowerCase();
        this.speakerId = parseSpeakerId(parts[2]);
    }

    private int parseSpeakerId(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public XiaozhiTtsOptions getOptions() {
        return options;
    }

    @MonitoredOperation(name = "xiaozhi.tts")
    @Override
    public Path textToSpeech(String text) throws Exception {
        try {
            OfflineTts tts = getOrCreateTts();
            float ttsSpeed = (getSpeed() != null) ? getSpeed().floatValue() : 1.0f;

            // 推理在池内，后面的转码与落盘在池外，工作线程只占住真正抢 CPU 的那一段
            GeneratedAudio audio = generateWithAdmission(tts, text, ttsSpeed);

            if (audio == null || audio.getSamples() == null || audio.getSamples().length == 0) {
                log.error("sherpa-onnx 语音合成返回空音频，模型路径: {}", modelPath);
                return null;
            }

            // 将 float[] samples 转为 16-bit PCM byte[]
            byte[] pcmData = AudioUtils.floatToPcm16(audio.getSamples());

            // 如果采样率不是16000，需要重采样
            int sampleRate = audio.getSampleRate();
            if (sampleRate != AudioUtils.SAMPLE_RATE) {
                pcmData = AudioUtils.resamplePcm(pcmData, sampleRate, AudioUtils.SAMPLE_RATE);
            }

            // 保存为 WAV 文件
            Path outPath = Path.of(outputPath, getAudioFileName());
            AudioUtils.saveAsWav(outPath, pcmData);

            return outPath;
        } catch (TtsOverloadException e) {
            throw e;
        } catch (Exception e) {
            log.error("sherpa-onnx 语音合成失败 - 模型路径: {}, 错误: {}", modelPath, e.getMessage(), e);
            throw new Exception("本地语音合成失败: " + e.getMessage());
        }
    }

    /**
     * 不得绕开本方法直接调 generate：不限并发时 N 句同时合成会开出 N×numThreads 条 native 线程抢核。
     */
    private GeneratedAudio generateWithAdmission(OfflineTts tts, String text, float speed) throws Exception {
        return submitWithAdmission(() -> {
            long start = System.nanoTime();
            GeneratedAudio generated = tts.generate(text, speakerId, speed);
            recordGenerate(start, generated);
            return generated;
        });
    }

    /**
     * 送进合成线程池并等结果。队列已满或排队超过等待上限即放弃本次，抛 {@link TtsOverloadException}。
     */
    static <T> T submitWithAdmission(Callable<T> task) throws Exception {
        Future<T> future;
        try {
            future = executor().submit(task);
        } catch (RejectedExecutionException e) {
            droppedCount.incrementAndGet();
            throw new TtsOverloadException("本地合成排队已满，跳过本次合成");
        }
        long timeout = waitTimeoutMs;
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 已进 JNI 的任务打断不了，cancel(false) 只撤掉还在排队的，避免白跑
            future.cancel(false);
            droppedCount.incrementAndGet();
            throw new TtsOverloadException("本地合成排队超过 " + timeout + "ms，跳过本次合成");
        } catch (InterruptedException e) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof Exception ex ? ex : new Exception(cause);
        }
    }

    private static void recordGenerate(long startNanos, GeneratedAudio audio) {
        long elapsedNanos = System.nanoTime() - startNanos;
        if (audio == null || audio.getSamples() == null || audio.getSamples().length == 0) {
            return;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        float audioDuration = audio.getSamples().length / (float) audio.getSampleRate();
        log.info("sherpa-onnx 语音合成完成 - 耗时: {}ms, 音频时长: {}s, RTF: {}",
                elapsedMs, String.format("%.2f", audioDuration),
                String.format("%.3f", (elapsedMs / 1000.0f) / audioDuration));
    }

    /**
     * 建合成线程池，由 {@code SherpaTtsConfig} 启动时调一次。
     * maxConcurrent 非正数时按核数的四分之一推导，waitTimeout 非正数时用缺省 8 秒。
     */
    public static synchronized void configure(int maxConcurrent, int numThreads, long waitTimeout) {
        synthNumThreads = Math.max(1, numThreads);
        waitTimeoutMs = waitTimeout > 0 ? waitTimeout : DEFAULT_WAIT_TIMEOUT_MS;
        if (synthExecutor == null) {
            synthExecutor = createExecutor(maxConcurrent > 0 ? maxConcurrent : DEFAULT_MAX_CONCURRENT);
        }
    }

    private static ThreadPoolExecutor executor() {
        ThreadPoolExecutor executor = synthExecutor;
        if (executor != null) {
            return executor;
        }
        synchronized (SherpaOnnxTtsService.class) {
            if (synthExecutor == null) {
                synthExecutor = createExecutor(DEFAULT_MAX_CONCURRENT);
            }
            return synthExecutor;
        }
    }

    private static ThreadPoolExecutor createExecutor(int maxConcurrent) {
        int queueCapacity = maxConcurrent * QUEUE_PER_PERMIT;
        log.info("sherpa-onnx TTS 合成线程池就绪 - 并发上限: {}, 排队上限: {}", maxConcurrent, queueCapacity);
        // 平台线程：推理在 JNI 里长时间占着 CPU，放虚拟线程会把载体线程钉死
        return new ThreadPoolExecutor(
                maxConcurrent, maxConcurrent,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "sherpa-tts-worker");
                    t.setDaemon(true);
                    return t;
                },
                REJECTION_HANDLER);
    }

    /**
     * 获取或创建 OfflineTts 实例（带缓存）
     */
    private OfflineTts getOrCreateTts() {
        String cacheKey = modelPath + ":" + modelType;
        return ttsCache.computeIfAbsent(cacheKey, k -> createTts());
    }

    /**
     * 根据模型类型创建 OfflineTts 实例
     */
    private OfflineTts createTts() {
        log.info("初始化 sherpa-onnx TTS 模型 - 类型: {}, 路径: {}, 线程数: {}", modelType, modelPath, synthNumThreads);

        OfflineTtsModelConfig.Builder modelConfigBuilder = OfflineTtsModelConfig.builder()
                .setNumThreads(synthNumThreads)
                .setDebug(false)
                .setProvider("cpu");

        OfflineTtsConfig.Builder ttsConfigBuilder = OfflineTtsConfig.builder();
        File dir = new File(modelPath);

        switch (modelType) {
            case "kokoro" -> {
                OfflineTtsKokoroModelConfig kokoroConfig = OfflineTtsKokoroModelConfig.builder()
                        .setModel(findFile(dir, "model.onnx"))
                        .setVoices(findFile(dir, "voices.bin"))
                        .setTokens(findFile(dir, "tokens.txt"))
                        .setDataDir(findDir(dir, "espeak-ng-data"))
                        .setLexicon(findLexicons(dir))
                        .build();
                modelConfigBuilder.setKokoro(kokoroConfig);
            }
            case "vits" -> {
                OfflineTtsVitsModelConfig vitsConfig = OfflineTtsVitsModelConfig.builder()
                        .setModel(findFile(dir, "model.onnx"))
                        .setTokens(findFile(dir, "tokens.txt"))
                        .setLexicon(findFileOptional(dir, "lexicon.txt"))
                        .setDataDir(findDirOptional(dir, "espeak-ng-data"))
                        .setDictDir(findDirOptional(dir, "dict"))
                        .build();
                modelConfigBuilder.setVits(vitsConfig);
                // 设置 rule fsts
                String ruleFsts = findRuleFsts(dir);
                if (!ruleFsts.isEmpty()) {
                    ttsConfigBuilder.setRuleFsts(ruleFsts);
                }
            }
            case "matcha" -> {
                OfflineTtsMatchaModelConfig matchaConfig = OfflineTtsMatchaModelConfig.builder()
                        .setAcousticModel(findFileByPattern(dir, "model-steps"))
                        .setVocoder(findFileByPattern(dir, "vocoder", "vocos"))
                        .setTokens(findFile(dir, "tokens.txt"))
                        .setLexicon(findFileOptional(dir, "lexicon.txt"))
                        .setDataDir(findDirOptional(dir, "espeak-ng-data"))
                        .setDictDir(findDirOptional(dir, "dict"))
                        .build();
                modelConfigBuilder.setMatcha(matchaConfig);
                String ruleFsts = findRuleFsts(dir);
                if (!ruleFsts.isEmpty()) {
                    ttsConfigBuilder.setRuleFsts(ruleFsts);
                }
            }
            default -> throw new RuntimeException("不支持的 sherpa-onnx TTS 模型类型: " + modelType);
        }

        OfflineTtsConfig config = ttsConfigBuilder
                .setModel(modelConfigBuilder.build())
                .build();

        return new OfflineTts(config);
    }

    // ========== 文件查找辅助方法 ==========

    private String findFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) {
            throw new RuntimeException("模型文件不存在: " + f.getAbsolutePath());
        }
        return f.getAbsolutePath();
    }

    private String findFileOptional(File dir, String name) {
        File f = new File(dir, name);
        return f.exists() ? f.getAbsolutePath() : "";
    }

    private String findDir(File dir, String name) {
        File d = new File(dir, name);
        if (!d.exists() || !d.isDirectory()) {
            throw new RuntimeException("模型目录不存在: " + d.getAbsolutePath());
        }
        return d.getAbsolutePath();
    }

    private String findDirOptional(File dir, String name) {
        File d = new File(dir, name);
        return (d.exists() && d.isDirectory()) ? d.getAbsolutePath() : "";
    }

    /**
     * 查找匹配任意一个模式的 .onnx 文件
     */
    private String findFileByPattern(File dir, String... patterns) {
        File[] files = dir.listFiles((d, n) -> {
            if (!n.endsWith(".onnx")) return false;
            for (String p : patterns) {
                if (n.contains(p)) return true;
            }
            return false;
        });
        if (files == null || files.length == 0) {
            throw new RuntimeException("未找到匹配 " + Arrays.toString(patterns) + " 的 .onnx 文件，目录: " + dir.getAbsolutePath());
        }
        return files[0].getAbsolutePath();
    }

    /**
     * 查找所有 lexicon 文件并用逗号连接
     */
    private String findLexicons(File dir) {
        File[] files = dir.listFiles((d, n) -> n.startsWith("lexicon") && n.endsWith(".txt"));
        if (files == null || files.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < files.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(files[i].getAbsolutePath());
        }
        return sb.toString();
    }

    /**
     * 查找所有 .fst 规则文件并用逗号连接
     */
    private String findRuleFsts(File dir) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".fst"));
        if (files == null || files.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < files.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(files[i].getAbsolutePath());
        }
        return sb.toString();
    }
}

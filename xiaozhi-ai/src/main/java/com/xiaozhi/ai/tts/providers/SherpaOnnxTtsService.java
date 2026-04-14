package com.xiaozhi.ai.tts.providers;

import com.k2fsa.sherpa.onnx.*;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.XiaozhiTtsOptions;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.utils.AudioUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 sherpa-onnx 的本地语音合成服务
 * 支持 VITS、Kokoro、Matcha 等多种本地 TTS 模型
 *
 * voiceName 格式：modelDir:modelType:speakerId
 *   示例：vits-melo-tts-zh_en:vits:0
 *         kokoro-multi-lang:kokoro:3
 *         matcha-zh-baker:matcha:0
 */
public class SherpaOnnxTtsService implements TtsService {
    private static final Logger logger = LoggerFactory.getLogger(SherpaOnnxTtsService.class);

    private static final String PROVIDER_NAME = "sherpa-onnx";

    // 缓存 OfflineTts 实例，避免重复加载模型（key = modelPath）
    private static final Map<String, OfflineTts> ttsCache = new ConcurrentHashMap<>();

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
            Float pitch,
            Float speed,
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

    @Override
    public Path textToSpeech(String text) throws Exception {
        try {
            OfflineTts tts = getOrCreateTts();
            float ttsSpeed = (getSpeed() != null) ? getSpeed() : 1.0f;

            long start = System.currentTimeMillis();
            GeneratedAudio audio = tts.generate(text, speakerId, ttsSpeed);
            long elapsed = System.currentTimeMillis() - start;

            if (audio == null || audio.getSamples() == null || audio.getSamples().length == 0) {
                logger.error("sherpa-onnx 语音合成返回空音频，模型路径: {}", modelPath);
                return null;
            }

            float audioDuration = audio.getSamples().length / (float) audio.getSampleRate();
            float rtf = (elapsed / 1000.0f) / audioDuration;
            logger.info("sherpa-onnx 语音合成完成 - 耗时: {}ms, 音频时长: {}s, RTF: {}",
                    elapsed, String.format("%.2f", audioDuration), String.format("%.3f", rtf));

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
        } catch (Exception e) {
            logger.error("sherpa-onnx 语音合成失败 - 模型路径: {}, 错误: {}", modelPath, e.getMessage(), e);
            throw new Exception("本地语音合成失败: " + e.getMessage());
        }
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
        logger.info("初始化 sherpa-onnx TTS 模型 - 类型: {}, 路径: {}", modelType, modelPath);

        OfflineTtsModelConfig.Builder modelConfigBuilder = OfflineTtsModelConfig.builder()
                .setNumThreads(2)
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
            throw new RuntimeException("未找到匹配 " + java.util.Arrays.toString(patterns) + " 的 .onnx 文件，目录: " + dir.getAbsolutePath());
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

    /**
     * 清除指定模型路径的缓存
     */
    public static void clearModelCache(String modelPath) {
        ttsCache.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(modelPath)) {
                try {
                    entry.getValue().release();
                } catch (Exception e) {
                    // ignore
                }
                return true;
            }
            return false;
        });
    }
}

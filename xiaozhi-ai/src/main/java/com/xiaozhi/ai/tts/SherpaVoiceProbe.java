package com.xiaozhi.ai.tts;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.model.resp.SherpaVoiceResp;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

/**
 * Sherpa-ONNX 本地音色扫描。
 * <p>
 * 扫描配置的本地 TTS 模型目录，自动识别模型类型（Kokoro / Matcha / VITS）和 speaker。
 * 扫描的是与 {@link com.xiaozhi.ai.tts.providers.SherpaOnnxTtsService} 相同的模型目录。
 */
@Component
public class SherpaVoiceProbe {

    // Kokoro voices.bin 是各说话人 float32 张量顺序拼接：v1.0 起每人 (510, 1, 256)，v0.19 每人 (511, 1, 256)
    private static final long[] KOKORO_SPEAKER_TENSOR_BYTES = {510L * 256L * 4L, 511L * 256L * 4L};

    private static final long CACHE_TTL_MILLIS = 60_000L;

    @Resource
    private RuntimePathConfig runtimePathConfig;

    private volatile List<SherpaVoiceResp> cachedVoices;
    private volatile long cachedAtMillis;

    /**
     * 扫描本地 TTS 模型目录，返回所有可用的 sherpa-onnx 音色列表。
     */
    public List<SherpaVoiceResp> listVoices() {
        List<SherpaVoiceResp> snapshot = cachedVoices;
        if (snapshot != null && DateUtils.millis() - cachedAtMillis < CACHE_TTL_MILLIS) {
            return snapshot;
        }
        synchronized (this) {
            snapshot = cachedVoices;
            if (snapshot != null && DateUtils.millis() - cachedAtMillis < CACHE_TTL_MILLIS) {
                return snapshot;
            }
            List<SherpaVoiceResp> scanned = scanVoices();
            cachedVoices = scanned;
            cachedAtMillis = DateUtils.millis();
            return scanned;
        }
    }

    private List<SherpaVoiceResp> scanVoices() {
        List<SherpaVoiceResp> voices = new ArrayList<>();
        File ttsDir = runtimePathConfig.resolveTtsModelsDir().toFile();
        if (!ttsDir.exists() || !ttsDir.isDirectory()) {
            return voices;
        }
        File[] modelDirs = ttsDir.listFiles(File::isDirectory);
        if (modelDirs != null) {
            Arrays.sort(modelDirs, Comparator.comparing(File::getName));
            for (File modelDir : modelDirs) {
                voices.addAll(buildVoicesForModel(modelDir));
            }
        }
        return voices;
    }

    private List<SherpaVoiceResp> buildVoicesForModel(File modelDir) {
        List<SherpaVoiceResp> voices = new ArrayList<>();
        String dirName = modelDir.getName();

        // 检测模型类型
        boolean isKokoro = new File(modelDir, "voices.bin").exists();
        boolean isMatcha = false;
        if (!isKokoro) {
            File[] files = modelDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if ((f.getName().contains("vocoder") || f.getName().contains("vocos")) && f.getName().endsWith(".onnx")) {
                        isMatcha = true;
                        break;
                    }
                }
            }
        }

        if (isKokoro) {
            int speakerCount = countKokoroSpeakers(new File(modelDir, "voices.bin"));
            for (int i = 0; i < speakerCount; i++) {
                voices.add(buildVoice(dirName, "kokoro", i, "Speaker-" + i));
            }
        } else if (isMatcha) {
            voices.add(buildVoice(dirName, "matcha", 0, dirName));
        } else {
            // VITS：多 speaker 模型通过目录名判断
            boolean isMultiSpeaker = dirName.contains("aishell3") || dirName.contains("vctk");
            if (isMultiSpeaker) {
                // 多 speaker VITS，默认列出前10个，用户可自行扩充
                for (int i = 0; i < 10; i++) {
                    voices.add(buildVoice(dirName, "vits", i, "Speaker-" + i));
                }
            } else {
                voices.add(buildVoice(dirName, "vits", 0, dirName));
            }
        }
        return voices;
    }

    private SherpaVoiceResp buildVoice(String modelDir, String modelType, int speakerId, String label) {
        return new SherpaVoiceResp(label, modelDir + ":" + modelType + ":" + speakerId, "sherpa-onnx", modelDir);
    }

    /**
     * 根据 voices.bin 文件大小推算 Kokoro speaker 数量。
     * sherpa-onnx 加载时要求文件恰好是「说话人数 × 每人张量」个 float32，按已发布的两种张量长度依次整除；
     * 都对不上时只列出一定存在的 0 号说话人。
     */
    private int countKokoroSpeakers(File voicesBin) {
        long size = voicesBin.length();
        if (size > 0) {
            for (long perSpeaker : KOKORO_SPEAKER_TENSOR_BYTES) {
                if (size % perSpeaker == 0) {
                    return (int) (size / perSpeaker);
                }
            }
        }
        return 1;
    }
}

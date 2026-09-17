package com.xiaozhi.ai.tts;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.model.resp.SherpaVoiceResp;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Sherpa-ONNX 本地音色扫描。
 * <p>
 * 扫描配置的本地 TTS 模型目录，自动识别模型类型（Kokoro / Matcha / VITS）和 speaker。
 * 扫描的是与 {@link com.xiaozhi.ai.tts.providers.SherpaOnnxTtsService} 相同的模型目录。
 */
@Slf4j
@Component
public class SherpaVoiceProbe {

    @Resource
    private RuntimePathConfig runtimePathConfig;

    /**
     * 扫描本地 TTS 模型目录，返回所有可用的 sherpa-onnx 音色列表。
     */
    public List<SherpaVoiceResp> listVoices() {
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
            List<String> speakerNames = readKokoroSpeakers(new File(modelDir, "voices.bin"));
            if (speakerNames.isEmpty()) {
                // 读取失败，默认给8个
                for (int i = 0; i < 8; i++) {
                    voices.add(buildVoice(dirName, "kokoro", i, "Speaker-" + i));
                }
            } else {
                for (int i = 0; i < speakerNames.size(); i++) {
                    voices.add(buildVoice(dirName, "kokoro", i, speakerNames.get(i)));
                }
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
     * 读取 Kokoro voices.bin 中的 speaker 名称列表。
     * 文件格式：每个名称以 \0 结尾连续存储。
     */
    private List<String> readKokoroSpeakers(File voicesBin) {
        List<String> names = new ArrayList<>();
        try {
            byte[] data = Files.readAllBytes(voicesBin.toPath());
            int start = 0;
            for (int i = 0; i < data.length; i++) {
                if (data[i] == 0) {
                    if (i > start) {
                        String name = new String(data, start, i - start, StandardCharsets.UTF_8).trim();
                        if (!name.isEmpty()) {
                            names.add(name);
                        }
                    }
                    start = i + 1;
                }
            }
            // 处理末尾没有 \0 的情况
            if (start < data.length) {
                String name = new String(data, start, data.length - start, StandardCharsets.UTF_8).trim();
                if (!name.isEmpty()) names.add(name);
            }
        } catch (IOException e) {
            log.warn("读取 voices.bin 失败: {}", voicesBin.getAbsolutePath());
        }
        return names;
    }
}

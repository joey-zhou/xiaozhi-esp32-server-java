package com.xiaozhi.websocket.stt.providers;

import java.io.ByteArrayInputStream;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.websocket.stt.SttService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.json.JSONObject;

/**
 * Vosk STT服务实现
 */
public class VoskSttService implements SttService {

    private static final Logger logger = LoggerFactory.getLogger(VoskSttService.class);
    private static final String PROVIDER_NAME = "vosk";

    // Vosk模型相关对象
    private Model model;
    private String voskModelPath;

    /**
     * 初始化Vosk模型
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
                System.load(System.getProperty("user.dir") + "/models/libvosk.dylib");
                logger.info("Vosk library loaded for macOS M-series chip.");
            } else {
                logger.info("Not macOS M-series chip, skipping Vosk library load.");
            }
            // 禁用Vosk日志输出
            LibVosk.setLogLevel(LogLevel.WARNINGS);

            // 加载模型，路径为配置的模型目录
            voskModelPath = System.getProperty("user.dir") + "/models/vosk-model";
            logger.debug(voskModelPath);
            model = new Model(voskModelPath);
            logger.info("Vosk 模型加载成功！路径: {}", voskModelPath);
        } catch (Exception e) {
            logger.warn("Vosk 模型加载失败！将使用其他STT服务: {}", e.getMessage());
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String recognition(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            logger.warn("音频数据为空！");
            return null;
        }

        // 将原始音频数据转换为MP3格式并保存
        String fileName = AudioUtils.saveAsMp3File(audioData);

        try (Recognizer recognizer = new Recognizer(model, 16000)) { // 16000 是采样率
            ByteArrayInputStream audioStream = new ByteArrayInputStream(audioData);

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = audioStream.read(buffer)) != -1) {
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    // 如果识别到完整的结果
                    return recognizer.getResult().replaceAll("\\s+", "");
                }
            }

            // 返回最终的识别结果
            return recognizer.getFinalResult().replaceAll("\\s+", "");

        } catch (Exception e) {
            logger.error("处理音频时发生错误！", e);
            return null;
        }
    }

    @Override
    public Flux<String> streamRecognition(Flux<byte[]> audioStream) {
        return Mono.fromCallable(() -> new Recognizer(model, 16000))
                .flatMapMany(recognizer -> {
                    return audioStream
                            .publishOn(Schedulers.boundedElastic())
                            .map(audioChunk -> {
                                try {
                                    boolean hasResult = recognizer.acceptWaveForm(audioChunk, audioChunk.length);
                                    if (hasResult) {
                                        // 提取部分识别结果中的文本
                                        String result = recognizer.getResult();
                                        JSONObject jsonResult = new JSONObject(result);
                                        if (jsonResult.has("text") && !jsonResult.getString("text").isEmpty()) {
                                            return jsonResult.getString("text").replaceAll("\\s+", "");
                                        }
                                    } else {
                                        // 获取部分识别结果
                                        String partialResult = recognizer.getPartialResult();
                                        JSONObject jsonPartial = new JSONObject(partialResult);
                                        if (jsonPartial.has("partial") && !jsonPartial.getString("partial").isEmpty()) {
                                            return jsonPartial.getString("partial").replaceAll("\\s+", "");
                                        }
                                    }
                                    return "";
                                } catch (Exception e) {
                                    logger.error("流式识别处理音频块时发生错误", e);
                                    return "";
                                }
                            })
                            .filter(text -> !text.isEmpty())
                            .concatWith(Mono.fromSupplier(() -> {
                                try {
                                    // 流结束时获取最终结果
                                    String finalResult = recognizer.getFinalResult().replaceAll("\\s+", "");
                                    JSONObject jsonFinal = new JSONObject(finalResult);

                                    recognizer.close();
                                    return jsonFinal.getString("text").replaceAll("\\s+", "");
                                } catch (Exception e) {
                                    logger.error("获取最终识别结果时发生错误", e);
                                    return "";
                                }
                            }))
                            .doOnError(e -> {
                                logger.error("流式识别过程中发生错误", e);
                                try {
                                    recognizer.close();
                                } catch (Exception ex) {
                                    logger.error("关闭识别器时发生错误", ex);
                                }
                            });
                })
                .onErrorResume(e -> {
                    logger.error("创建Vosk识别器时发生错误", e);
                    return Flux.empty();
                });
    }
}

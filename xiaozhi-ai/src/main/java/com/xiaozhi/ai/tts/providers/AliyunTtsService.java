package com.xiaozhi.ai.tts.providers;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import com.xiaozhi.ai.tts.TtsService;
import com.xiaozhi.ai.tts.XiaozhiTtsOptions;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.utils.AudioUtils;


import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AliyunTtsService implements TtsService {
    private static final String PROVIDER_NAME = "aliyun";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final long TTS_TIMEOUT_SECONDS = 5;

    private static final ExecutorService sharedExecutor = new ThreadPoolExecutor(
            0, 20, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "aliyun-tts-worker");
                t.setDaemon(true);
                return t;
            });

    static {
        // 注册JVM关闭钩子，确保线程池被正确关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sharedExecutor.shutdown();
            try {
                if (!sharedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    sharedExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                sharedExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }, "aliyun-tts-shutdown"));
    }
    
    // 音色映射表：将音色名称映射到AudioParameters.Voice枚举
    // 包含所有Qwen音色
    private static final Map<String, AudioParameters.Voice> VOICE_MAP = new HashMap<>();

    static {
        VOICE_MAP.put("Cherry", AudioParameters.Voice.CHERRY);          // 芊悦 - 阳光积极、亲切自然小姐姐
        VOICE_MAP.put("Ethan", AudioParameters.Voice.ETHAN);            // 晨煦 - 标准普通话，阳光温暖
        VOICE_MAP.put("Nofish", AudioParameters.Voice.NOFISH);          // 不吃鱼 - 不会翘舌音的设计师
        VOICE_MAP.put("Jennifer", AudioParameters.Voice.JENNIFER);      // 詹妮弗 - 品牌级、电影质感般美语女声
        VOICE_MAP.put("Ryan", AudioParameters.Voice.RYAN);              // 甜茶 - 节奏拉满，戏感炸裂
        VOICE_MAP.put("Katerina", AudioParameters.Voice.KATERINA);      // 卡捷琳娜 - 御姐音色，韵律回味十足
        VOICE_MAP.put("Elias", AudioParameters.Voice.ELIAS);            // 墨讲师 - 学科严谨性与叙事技巧
        VOICE_MAP.put("Jada", AudioParameters.Voice.JADA);              // 上海-阿珍 - 风风火火的沪上阿姐
        VOICE_MAP.put("Dylan", AudioParameters.Voice.DYLAN);            // 北京-晓东 - 北京胡同里长大的少年
        VOICE_MAP.put("Sunny", AudioParameters.Voice.SUNNY);            // 四川-晴儿 - 甜到你心里的川妹子
        VOICE_MAP.put("Li", AudioParameters.Voice.LI);                  // 南京-老李 - 耐心的瑜伽老师
        VOICE_MAP.put("Marcus", AudioParameters.Voice.MARCUS);          // 陕西-秦川 - 面宽话短，心实声沉
        VOICE_MAP.put("Roy", AudioParameters.Voice.ROY);                // 闽南-阿杰 - 诙谐直爽、市井活泼
        VOICE_MAP.put("Peter", AudioParameters.Voice.PETER);            // 天津-李彼得 - 天津相声，专业捧人
        VOICE_MAP.put("Rocky", AudioParameters.Voice.ROCKY);            // 粤语-阿强 - 幽默风趣的阿强
        VOICE_MAP.put("Kiki", AudioParameters.Voice.KIKI);              // 粤语-阿清 - 甜美的港妹闺蜜
        VOICE_MAP.put("Eric", AudioParameters.Voice.ERIC);              // 四川-程川 - 跳脱市井的四川成都男子
        VOICE_MAP.put("Serena", AudioParameters.Voice.SERENA);              // 苏瑶 - 温柔小姐姐
        VOICE_MAP.put("Chelsie", AudioParameters.Voice.CHELSIE);            // 千雪 - 二次元虚拟女友
        VOICE_MAP.put("Momo", AudioParameters.Voice.MOMO);                  // 茉兔 - 撒娇搞怪，逗你开心
        VOICE_MAP.put("Moon", AudioParameters.Voice.MOON);                  // 月白 - 率性帅气的月白
        VOICE_MAP.put("Maia", AudioParameters.Voice.MAIA);                  // 四月 - 知性与温柔的碰撞
        VOICE_MAP.put("Kai", AudioParameters.Voice.KAI);                    // 凯 - 耳朵的一场SPA
        VOICE_MAP.put("Bella", AudioParameters.Voice.BELLA);                // 萌宝 - 喝酒不打醉拳的小萝莉
        VOICE_MAP.put("Aiden", AudioParameters.Voice.AIDEN);                // 艾登 - 精通厨艺的美语大男孩
        VOICE_MAP.put("EldricSage", AudioParameters.Voice.ELDRIC_SAGE);     // 沧明子 - 沉稳睿智的老者
        VOICE_MAP.put("Mia", AudioParameters.Voice.MIA);                    // 乖小妹 - 温顺如春水，乖巧如初雪
        VOICE_MAP.put("Bellona", AudioParameters.Voice.BELLONA);            // 燕铮莺 - 声音洪亮，吐字清晰
        VOICE_MAP.put("Vincent", AudioParameters.Voice.VINCENT);            // 田叔 - 一口独特的沙哑烟嗓
        VOICE_MAP.put("Bunny", AudioParameters.Voice.BUNNY);                // 萌小姬 - 萌系可爱女声
        VOICE_MAP.put("Arthur", AudioParameters.Voice.ARTHUR);              // 徐大爷 - 被岁月和旱烟浸泡过的质朴嗓音
        VOICE_MAP.put("Ebona", AudioParameters.Voice.EBONA);                // 诡婆婆 - 低语像一把生锈的钥匙
        VOICE_MAP.put("Seren", AudioParameters.Voice.SEREN);                // 小婉 - 温和舒缓的声线
        VOICE_MAP.put("Bodega", AudioParameters.Voice.BODEGA);              // 博德加 - 热情的西班牙大叔
        VOICE_MAP.put("Sonrisa", AudioParameters.Voice.SONRISA);            // 索尼莎 - 热情开朗的拉美大姐
        VOICE_MAP.put("Alek", AudioParameters.Voice.ALEK);                  // 阿列克 - 战斗民族的冷与暖
        VOICE_MAP.put("OnoAnna", AudioParameters.Voice.ONO_ANNA);           // 小野杏 - 鬼灵精怪的青梅竹马
        VOICE_MAP.put("Lenn", AudioParameters.Voice.LENN);                  // 莱恩 - 理性是底色，叛逆藏在细节里
        VOICE_MAP.put("Emilien", AudioParameters.Voice.EMILIEN);            // 埃米尔安 - 浪漫的法国大哥哥
        VOICE_MAP.put("Andre", AudioParameters.Voice.ANDRE);                // 安德雷 - 声音磁性
    }

    // 阿里云配置
    private final String apiKey;
    private final XiaozhiTtsOptions options;
    private final String outputPath;

    public AliyunTtsService(ConfigBO config,
            String voiceName, Float pitch, Float speed, String outputPath) {
        this.apiKey = config.getApiKey();
        this.options = XiaozhiTtsOptions.builder().voiceName(voiceName).pitch(pitch).speed(speed).build();
        this.outputPath = outputPath;
    }

    /**
     * 解析千问音色参数，支持格式：
     * 1. "qwen3-tts-flash-realtime:Cherry" - 指定模型和音色（冒号分隔）
     * 2. "qwen3-tts-instruct-flash-realtime:Cherry" - 指定模型和音色（冒号分隔）
     * 3. "qwen-tts-realtime:Cherry" - 指定模型和音色（冒号分隔）
     * 4. "Cherry" - 只有音色，默认使用 qwen3-tts-flash-realtime
     *
     * @param voiceParam 音色参数
     * @return [模型名, 音色名]
     */
    private String[] parseQwenVoiceParam(String voiceParam) {
        if (voiceParam == null || voiceParam.isEmpty()) {
            return new String[]{"qwen3-tts-flash-realtime", voiceParam};
        }

        // 检查是否包含模型前缀（冒号分隔格式）
        if (voiceParam.contains(":")) {
            String[] parts = voiceParam.split(":", 2);
            String model = parts[0];
            String voice = parts.length > 1 ? parts[1] : "";

            // 验证模型名称是否为有效的千问模型
            if (model.startsWith("qwen") && model.contains("tts")) {
                return new String[]{model, voice};
            }
            // 如果模型名称无效，将整个字符串视为音色名
            log.warn("无效的千问模型名称: {}, 使用默认模型 qwen3-tts-flash-realtime", model);
            return new String[]{"qwen3-tts-flash-realtime", voiceParam};
        }

        // 没有模型前缀，使用默认模型
        return new String[]{"qwen3-tts-flash-realtime", voiceParam};
    }

    /**
     * 解析音色参数，支持格式：
     * 1. "cosyvoice-v3-plus-voiceclone-xxx" - 音色克隆返回的格式，自动识别模型前缀
     * 2. "cosyvoice-v3-flash-voiceclone-xxx" - 音色克隆返回的格式，自动识别模型前缀
     * 3. "cosyvoice-v2-voiceclone-xxx" - 音色克隆返回的格式，自动识别模型前缀
     * 4. "cosyvoice-v2:longanyang" - 指定模型和音色（冒号分隔）
     * 5. "cosyvoice-v3-flash:longanyang" - 指定模型和音色（冒号分隔）
     * 6. "cosyvoice-v3-plus:longanyang" - 指定模型和音色（冒号分隔）
     * 7. "longanyang" - 只有音色，默认使用 cosyvoice-v2
     *
     * @param voiceParam 音色参数
     * @return [模型名, 音色名]
     */
    private String[] parseCosyVoiceParam(String voiceParam) {
        if (voiceParam == null || voiceParam.isEmpty()) {
            return new String[]{"cosyvoice-v2", voiceParam};
        }

        // 检查是否是音色克隆返回的格式（如：cosyvoice-v3-plus-voiceclone-xxx）
        if (voiceParam.startsWith("cosyvoice-v3-plus-")) {
            return new String[]{"cosyvoice-v3-plus", voiceParam};
        } else if (voiceParam.startsWith("cosyvoice-v3-flash-")) {
            return new String[]{"cosyvoice-v3-flash", voiceParam};
        } else if (voiceParam.startsWith("cosyvoice-v2-")) {
            return new String[]{"cosyvoice-v2", voiceParam};
        }

        // 检查是否包含模型前缀（冒号分隔格式，如：cosyvoice-v3-plus:longanyang）
        if (voiceParam.contains(":")) {
            String[] parts = voiceParam.split(":", 2);
            String model = parts[0];
            String voice = parts.length > 1 ? parts[1] : "";

            // 验证模型名称是否为有效的 CosyVoice 模型
            if ("cosyvoice-v2".equals(model) || "cosyvoice-v3-flash".equals(model) || "cosyvoice-v3-plus".equals(model)) {
                return new String[]{model, voice};
            }
            // 如果模型名称无效，将整个字符串视为音色名
            log.warn("无效的 CosyVoice 模型名称: {}, 使用默认模型 cosyvoice-v2", model);
            return new String[]{"cosyvoice-v2", voiceParam};
        }

        // 没有模型前缀，使用默认模型
        return new String[]{"cosyvoice-v2", voiceParam};
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
            if (getVoiceName().contains("sambert")) {
                return ttsSambert(text);
            } else {
                // 解析千问音色参数
                String[] parsed = parseQwenVoiceParam(getVoiceName());
                String actualVoiceName = parsed[1];

                if (VOICE_MAP.get(actualVoiceName) != null) {
                    return ttsQwen(text);
                } else {
                    return ttsCosyvoice(text);
                }
            }
        } catch (Exception e) {
            log.error("语音合成aliyun -使用{}模型语音合成失败：", getVoiceName(), e);
            throw new Exception("语音合成失败");
        }
    }

    private Path ttsQwen(String text) {
        int attempts = 0;
        // 解析音色参数
        String[] parsed = parseQwenVoiceParam(getVoiceName());
        String actualVoiceName = parsed[1];

        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                AudioParameters.Voice voice = VOICE_MAP.get(actualVoiceName);
                MultiModalConversationParam param = MultiModalConversationParam.builder()
                        // 非实时SDK（MultiModalConversation）只支持 qwen3-tts-flash，不能用 realtime 模型名
                        .model("qwen3-tts-flash")
                        .apiKey(apiKey)
                        .text(text)
                        .voice(voice)
                        .build();
                
                // 使用共享线程池而不是每次创建新的
                Future<MultiModalConversationResult> future = sharedExecutor.submit(() -> {
                    MultiModalConversation conv = new MultiModalConversation();
                    return conv.call(param);
                });
                
                // 等待结果，设置超时
                MultiModalConversationResult result;
                try {
                    result = future.get(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    log.warn("语音合成aliyun - 使用{}模型超时，正在重试 ({}/{})", getVoiceName(), attempts + 1, MAX_RETRY_ATTEMPTS);
                    attempts++;
                    if (attempts >= MAX_RETRY_ATTEMPTS) {
                        log.error("语音合成aliyun - 使用{}模型多次超时，放弃重试", getVoiceName());
                        return null;
                    }
                    // 等待一段时间后重试
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    continue;
                }

                // 检查结果是否有效
                if (result == null || result.getOutput() == null ||
                    result.getOutput().getAudio() == null ||
                    result.getOutput().getAudio().getUrl() == null) {

                    log.warn("语音合成aliyun - 使用{}模型返回无效结果，正在重试 ({}/{})", getVoiceName(), attempts + 1, MAX_RETRY_ATTEMPTS);
                    attempts++;
                    if (attempts >= MAX_RETRY_ATTEMPTS) {
                        log.error("语音合成aliyun - 使用{}模型多次返回无效结果，放弃重试", getVoiceName());
                        return null;
                    }
                    // 等待一段时间后重试
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    continue;
                }

                String audioUrl = result.getOutput().getAudio().getUrl();
                Path outPath = Path.of(outputPath, getAudioFileName());

                // 下载 WAV（24kHz），重采样到 16kHz 后保存
                Future<Boolean> downloadFuture = sharedExecutor.submit(() -> {
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                         InputStream in = URI.create(audioUrl).toURL().openStream()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }
                        byte[] pcm24k = AudioUtils.wavToPcm(baos.toByteArray());
                        byte[] pcm16k = AudioUtils.resamplePcm(pcm24k, 24000, 16000);
                        AudioUtils.saveAsWav(outPath, pcm16k);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });

                try {
                    Boolean downloadSuccess = downloadFuture.get(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (!downloadSuccess) {
                        throw new IOException("下载音频文件失败");
                    }
                } catch (TimeoutException e) {
                    downloadFuture.cancel(true);
                    log.warn("语音合成aliyun - 使用{}模型下载音频超时，正在重试 ({}/{})", getVoiceName(), attempts + 1, MAX_RETRY_ATTEMPTS);
                    attempts++;
                    if (attempts >= MAX_RETRY_ATTEMPTS) {
                        log.error("语音合成aliyun - 使用{}模型多次下载超时，放弃重试", getVoiceName());
                        return null;
                    }
                    // 等待一段时间后重试
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    continue;
                }

                return outPath;
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    log.warn("语音合成aliyun - 使用{}模型失败，正在重试 ({}/{}): {}", getVoiceName(), attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        // 等待一段时间后重试
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试等待被中断", ie);
                        return null;
                    }
                } else {
                    log.error("语音合成aliyun - 使用{}模型语音合成失败，已达到最大重试次数：", getVoiceName(), e);
                    return null;
                }
            }
        }
        return null;
    }

    // cosyvoice默认并发只有3个，所以需要增加一个重试机制
    private Path ttsCosyvoice(String text) {
        int attempts = 0;
        // 解析音色参数，获取模型名和音色名
        String[] parsed = parseCosyVoiceParam(getVoiceName());
        String modelName = parsed[0];
        String actualVoiceName = parsed[1];
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam param =
                com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam.builder()
                                .apiKey(apiKey)
                                .model(modelName)  // 使用解析出的模型名
                                .voice(actualVoiceName)  // 使用解析出的音色名
                                .speechRate(getSpeed())
                                .pitchRate(getPitch())
                                .format(com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat.WAV_16000HZ_MONO_16BIT)
                                .build();

                // 使用共享线程池
                Future<ByteBuffer> future = sharedExecutor.submit(() -> {
                    com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer synthesizer =
                        new com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer(param, null);
                    try {
                        return synthesizer.call(text);
                    } finally {
                        // 主动关闭WebSocket连接，避免僵尸连接占满连接池
                        try {
                            synthesizer.getDuplexApi().close(1000, "completed");
                        } catch (Exception e) {
                            log.debug("关闭CosyVoice TTS连接时发生错误", e);
                        }
                    }
                });

                // 等待结果，设置超时
                ByteBuffer audio;
                try {
                    audio = future.get(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    log.warn("语音合成aliyun - 使用{}模型超时，正在重试 ({}/{}) - 音色: {}", modelName, attempts + 1, MAX_RETRY_ATTEMPTS, actualVoiceName);
                    attempts++;
                    if (attempts >= MAX_RETRY_ATTEMPTS) {
                        log.error("语音合成aliyun - 使用{}模型多次超时，放弃重试 - 音色: {}", modelName, actualVoiceName);
                        return null;
                    }
                    // 等待一段时间后重试
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    continue;
                }

                // 检查返回的ByteBuffer是否为null
                if (audio == null) {
                    attempts++;
                    if (attempts < MAX_RETRY_ATTEMPTS) {
                        log.warn("语音合成aliyun - 使用{}模型返回null，正在重试 ({}/{}) - 音色: {}", modelName, attempts, MAX_RETRY_ATTEMPTS, actualVoiceName);
                        // 等待一段时间后重试
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                        continue;
                    } else {
                        log.error("语音合成aliyun - 使用{}模型多次返回null，放弃重试 - 音色: {}", modelName, actualVoiceName);
                        return null;
                    }
                }

                Path outPath = Path.of(outputPath, getAudioFileName());
                try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                    fos.write(audio.array());
                } catch (IOException e) {
                    log.error("语音合成aliyun -使用{}模型语音合成失败 - 音色: {}", modelName, actualVoiceName, e);
                    return null;
                }
                return outPath;
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    log.warn("语音合成aliyun - 使用{}模型失败，正在重试 ({}/{}) - 音色: {}: {}", modelName, attempts, MAX_RETRY_ATTEMPTS, actualVoiceName, e.getMessage());
                    try {
                        // 等待一段时间后重试
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试等待被中断 - 模型: {}", modelName, ie);
                        return null;
                    }
                } else {
                    log.error("语音合成aliyun -使用{}模型语音合成失败，已达到最大重试次数 - 音色: {}", modelName, actualVoiceName, e);
                    return null;
                }
            }
        }
        return null;
    }

    public Path ttsSambert(String text) {
        int attempts = 0;
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                        .apiKey(apiKey)
                        .model(getVoiceName())
                        .text(text)
                        .rate(getSpeed())
                        .pitch(getPitch())
                        .sampleRate(AudioUtils.SAMPLE_RATE)
                        .format(SpeechSynthesisAudioFormat.WAV)
                        .build();
                
                // 使用共享线程池
                Future<ByteBuffer> future = sharedExecutor.submit(() -> {
                    SpeechSynthesizer synthesizer = new SpeechSynthesizer();
                    return synthesizer.call(param);
                });
                
                // 等待结果，设置超时
                ByteBuffer audio;
                try {
                    audio = future.get(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    log.warn("语音合成aliyun - 使用{}模型超时，正在重试 ({}/{})，文本：{}", getVoiceName(), attempts + 1, MAX_RETRY_ATTEMPTS, text);
                    attempts++;
                    if (attempts >= MAX_RETRY_ATTEMPTS) {
                        log.error("语音合成aliyun - 使用{}模型多次超时，放弃重试，文本：{}", getVoiceName(), text);
                        return null;
                    }
                    // 等待一段时间后重试
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    continue;
                }
                
                // 检查返回的ByteBuffer是否为null
                if (audio == null) {
                    attempts++;
                    if (attempts < MAX_RETRY_ATTEMPTS) {
                        log.warn("语音合成aliyun - 使用{}模型返回null，正在重试 ({}/{})", getVoiceName(), attempts, MAX_RETRY_ATTEMPTS);
                        // 等待一段时间后重试
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                        continue;
                    } else {
                        log.error("语音合成aliyun - 使用{}模型多次返回null，放弃重试", getVoiceName());
                        return null;
                    }
                }
                
                Path outPath = Path.of(outputPath, getAudioFileName());
                try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                    fos.write(audio.array());
                } catch (IOException e) {
                    log.error("语音合成aliyun - 使用{}模型失败：", getVoiceName(), e);
                    return null;
                }
                return outPath;
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    log.warn("语音合成aliyun - 使用{}模型失败，正在重试 ({}/{}): {}", getVoiceName(), attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        // 等待一段时间后重试
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试等待被中断", ie);
                        return null;
                    }
                } else {
                    log.error("语音合成aliyun - 使用{}模型失败，已达到最大重试次数：", getVoiceName(), e);
                    return null;
                }
            }
        }
        return null;
    }
}
package com.xiaozhi.dialogue.stt.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tencent.asrv2.SpeechRecognizer;
import com.tencent.asrv2.SpeechRecognizerListener;
import com.tencent.asrv2.SpeechRecognizerRequest;
import com.tencent.asrv2.SpeechRecognizerResponse;
import com.tencent.core.ws.Credential;
import com.tencent.core.ws.SpeechClient;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.HttpUtil;

import okhttp3.*;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;

public class TencentSttService implements SttService {
    private static final Logger logger = LoggerFactory.getLogger(TencentSttService.class);
    private static final String PROVIDER_NAME = "tencent";
    private static final String API_URL = "https://asr.tencentcloudapi.com";
    private static final String API_VERSION = "2019-06-14";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String FORMAT = "pcm"; // 支持的音频格式：pcm, wav, mp3
    private static final int QUEUE_TIMEOUT_MS = 100; // 队列等待超时时间
    private static final long RECOGNITION_TIMEOUT_MS = 90000; // 识别超时时间（90秒）

    // 使用腾讯云SDK的默认URL
    private static final String WS_API_URL = "wss://asr.cloud.tencent.com/asr/v2/";

    private String secretId;
    private String secretKey;
    private String appId;

    private final static OkHttpClient client = HttpUtil.client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 全局共享的SpeechClient实例
    private final SpeechClient speechClient = new SpeechClient(WS_API_URL);

    // 存储当前活跃的识别会话
    private final ConcurrentHashMap<String, SpeechRecognizer> activeRecognizers = new ConcurrentHashMap<>();

    static {
        Thread.startVirtualThread(() -> {
            try {
                Request request = new Request.Builder().url(API_URL).head().build();
                Response response = client.newCall(request).execute();
                response.close(); // 不读取内容，仅建立连接，用以提速后续的请求
            } catch (Exception e) {
                logger.error("初始化TencentSttService TTS服务时发生错误", e);
            }
        });
    }

    public TencentSttService(SysConfig config) {
        if (config != null) {
            this.secretId = config.getApiKey();
            this.secretKey = config.getApiSecret();
            this.appId = config.getAppId();
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String recognition(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            logger.warn("音频数据为空！");
            return null;
        }

        // 将原始音频数据转换为MP3格式并保存（用于调试）
        String fileName = AudioUtils.saveAsWav(audioData);

        try {
            // 检查配置是否已设置
            if (secretId == null || secretKey == null) {
                logger.error("腾讯云语音识别配置未设置，无法进行识别");
                return null;
            }

            // 将音频数据转换为Base64编码
            String base64Audio = Base64.getEncoder().encodeToString(audioData);

            // 构建请求体
            String requestBody = buildRequestBody(base64Audio);

            // 获取认证头
            String[] authHeaders = getAuthHeaders(requestBody);

            // 发送请求
            String result = sendRequest(requestBody, authHeaders);

            return result;
        } catch (Exception e) {
            logger.error("处理音频时发生错误！", e);
            return null;
        }
    }

    @Override
    public String streamRecognition(Sinks.Many<byte[]> audioSink) {
        // 检查配置是否已设置
        if (secretId == null || secretKey == null || appId == null) {
            logger.error("腾讯云语音识别配置未设置，无法进行识别");
            return null;
        }

        // 使用阻塞队列存储音频数据
        BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        AtomicReference<String> finalResult = new AtomicReference<>("");
        CountDownLatch recognitionLatch = new CountDownLatch(1);
        
        // 订阅Sink并将数据放入队列
        audioSink.asFlux().subscribe(
            data -> audioQueue.offer(data),
            error -> {
                logger.error("音频流处理错误", error);
                isCompleted.set(true);
            },
            () -> isCompleted.set(true)
        );

        // 生成唯一的语音ID
        String voiceId = UUID.randomUUID().toString();

        try {
            // 创建腾讯云凭证
            Credential credential = new Credential(appId, secretId, secretKey);

            // 创建识别请求
            SpeechRecognizerRequest request = SpeechRecognizerRequest.init();
            request.setEngineModelType("16k_zh"); // 16k采样率中文模型
            request.setVoiceFormat(1); // PCM格式
            request.setVoiceId(voiceId);

            // 创建识别监听器
            SpeechRecognizerListener listener = new SpeechRecognizerListener() {
                private final StringBuilder textBuilder = new StringBuilder();
                
                @Override
                public void onRecognitionStart(SpeechRecognizerResponse response) {
                    logger.debug("腾讯云识别开始 - VoiceId: {}", voiceId);
                }

                @Override
                public void onSentenceBegin(SpeechRecognizerResponse response) {
                    // 句子开始，可以不处理
                }

                @Override
                public void onRecognitionResultChange(SpeechRecognizerResponse response) {
                    // 非稳态结果，可能会变化
                    if (response.getResult() != null && response.getResult().getVoiceTextStr() != null) {
                        String text = response.getResult().getVoiceTextStr();
                        if (!text.isEmpty()) {
                            // 更新当前识别结果
                            synchronized (textBuilder) {
                                textBuilder.setLength(0);
                                textBuilder.append(text);
                            }
                        }
                    }
                }

                @Override
                public void onSentenceEnd(SpeechRecognizerResponse response) {
                    // 稳态结果，不再变化
                    if (response.getResult() != null && response.getResult().getVoiceTextStr() != null) {
                        String text = response.getResult().getVoiceTextStr();
                        if (!text.isEmpty()) {
                            // 更新最终结果
                            synchronized (textBuilder) {
                                textBuilder.setLength(0);
                                textBuilder.append(text);
                            }
                            finalResult.set(text);
                        }
                    }
                }

                @Override
                public void onRecognitionComplete(SpeechRecognizerResponse response) {
                    // 识别完成，获取最终结果
                    if (response.getResult() != null && response.getResult().getVoiceTextStr() != null) {
                        String text = response.getResult().getVoiceTextStr();
                        if (!text.isEmpty()) {
                            finalResult.set(text);
                        } else {
                            // 如果最终结果为空，使用之前积累的结果
                            synchronized (textBuilder) {
                                if (textBuilder.length() > 0) {
                                    finalResult.set(textBuilder.toString());
                                }
                            }
                        }
                    }
                    
                    // 释放锁，表示识别完成
                    recognitionLatch.countDown();
                    
                    // 从活跃识别器中移除
                    activeRecognizers.remove(voiceId);
                }

                @Override
                public void onFail(SpeechRecognizerResponse response) {
                    logger.error("识别失败 - VoiceId: {}, 错误: {}", voiceId,
                            response.getMessage() != null ? response.getMessage() : "未知错误");
                    
                    // 释放锁，表示识别失败
                    recognitionLatch.countDown();
                    
                    // 从活跃识别器中移除
                    activeRecognizers.remove(voiceId);
                }

                @Override
                public void onMessage(SpeechRecognizerResponse response) {
                    // 可以记录所有消息，但不需要特别处理
                }
            };

            // 创建识别器
            SpeechRecognizer recognizer = new SpeechRecognizer(speechClient, credential, request, listener);

            // 存储到活跃识别器映射中
            activeRecognizers.put(voiceId, recognizer);

            // 启动识别器
            recognizer.start();

            // 标记是否已经发送了停止信号
            AtomicBoolean stopSent = new AtomicBoolean(false);

            // 启动虚拟线程发送音频数据
            Thread.startVirtualThread(() -> {
                try {
                    while (!isCompleted.get() || !audioQueue.isEmpty()) {
                        byte[] audioChunk = null;
                        try {
                            audioChunk = audioQueue.poll(QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            logger.warn("音频数据队列等待被中断", e);
                            Thread.currentThread().interrupt(); // 重新设置中断标志
                            break;
                        }
                        
                        if (audioChunk != null && activeRecognizers.containsKey(voiceId)) {
                            try {
                                recognizer.write(audioChunk);
                            } catch (Exception e) {
                                logger.error("发送音频数据时发生错误 - VoiceId: {}", voiceId, e);
                                break;
                            }
                        }
                    }
                    
                    // 发送停止信号
                    if (activeRecognizers.containsKey(voiceId) && !stopSent.getAndSet(true)) {
                        try {
                            recognizer.stop();
                        } catch (Exception e) {
                            logger.error("停止识别器时发生错误 - VoiceId: {}", voiceId, e);
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理音频流时发生错误 - VoiceId: {}", voiceId, e);
                }
            });

            // 等待识别完成或超时
            boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            if (!recognized) {
                // 超时后清理资源
                if (activeRecognizers.containsKey(voiceId)) {
                    try {
                        recognizer.stop();
                        recognizer.close();
                        activeRecognizers.remove(voiceId);
                    } catch (Exception e) {
                        logger.error("清理超时识别器资源时发生错误 - VoiceId: {}", voiceId, e);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("创建语音识别会话时发生错误", e);
        }
        
        return finalResult.get();
    }

    // 在服务关闭时释放资源
    public void shutdown() {
        // 关闭所有活跃的识别器
        activeRecognizers.forEach((id, recognizer) -> {
            try {
                recognizer.stop();
                recognizer.close();
            } catch (Exception e) {
                logger.error("关闭识别器时发生错误 - VoiceId: {}", id, e);
            }
        });
        activeRecognizers.clear();

        // 关闭SpeechClient
        speechClient.shutdown();
    }

    /**
     * 构建请求体
     */
    private String buildRequestBody(String base64Audio) throws Exception {
        // 构建请求参数
        ObjectNode requestMap = objectMapper.createObjectNode();
        requestMap.put("ProjectId", 0);
        requestMap.put("SubServiceType", 2); // 一句话识别
        requestMap.put("EngSerViceType", "16k_zh"); // 中文普通话通用
        requestMap.put("SourceType", 1); // 音频数据来源为语音文件
        requestMap.put("VoiceFormat", FORMAT); // 音频格式
        requestMap.put("Data", base64Audio); // Base64编码的音频数据
        requestMap.put("DataLen", base64Audio.length()); // 数据长度
        return objectMapper.writeValueAsString(requestMap);
    }

    /**
     * 获取认证头
     */
    private String[] getAuthHeaders(String requestBody) {
        try {
            // 获取当前UTC时间戳
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
            String timestamp = String.valueOf(now.toEpochSecond());
            String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // 服务名称必须是 "asr"
            String service = "asr";

            // 拼接凭证范围
            String credentialScope = date + "/" + service + "/tc3_request";

            // 使用TC3-HMAC-SHA256签名方法
            String algorithm = "TC3-HMAC-SHA256";

            // 构建规范请求字符串
            String httpRequestMethod = "POST";
            String canonicalUri = "/";
            String canonicalQueryString = "";

            // 注意：头部信息需要按照ASCII升序排列，且key和value都转为小写
            // 必须包含content-type和host头部
            String contentType = "application/json; charset=utf-8";
            String host = "asr.tencentcloudapi.com";
            String action = "SentenceRecognition"; // 接口名称

            // 构建规范头部信息，注意顺序和格式
            String canonicalHeaders = "content-type:" + contentType.toLowerCase() + "\n" +
                    "host:" + host.toLowerCase() + "\n" +
                    "x-tc-action:" + action.toLowerCase() + "\n";

            String signedHeaders = "content-type;host;x-tc-action";

            // 请求体哈希值
            String payloadHash = sha256Hex(requestBody);

            // 构建规范请求字符串
            String canonicalRequest = httpRequestMethod + "\n" +
                    canonicalUri + "\n" +
                    canonicalQueryString + "\n" +
                    canonicalHeaders + "\n" +
                    signedHeaders + "\n" +
                    payloadHash;

            // 计算规范请求的哈希值
            String hashedCanonicalRequest = sha256Hex(canonicalRequest);

            // 构建待签名字符串
            String stringToSign = algorithm + "\n" +
                    timestamp + "\n" +
                    credentialScope + "\n" +
                    hashedCanonicalRequest;

            // 计算签名密钥
            byte[] secretDate = hmacSha256("TC3" + secretKey, date);
            byte[] secretService = hmacSha256(secretDate, service);
            byte[] secretSigning = hmacSha256(secretService, "tc3_request");

            // 计算签名
            String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));

            // 构建授权头
            String authorization = algorithm + " " +
                    "Credential=" + secretId + "/" + credentialScope + ", " +
                    "SignedHeaders=" + signedHeaders + ", " +
                    "Signature=" + signature;

            return new String[] {
                    timestamp,
                    authorization
            };
        } catch (Exception e) {
            logger.error("生成认证头失败", e);
            throw new RuntimeException("生成认证头失败", e);
        }
    }

    /**
     * 发送请求到腾讯云API
     */
    private String sendRequest(String requestBody, String[] authHeaders) throws IOException {
        String timestamp = authHeaders[0];
        String authorization = authHeaders[1];

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Host", "asr.tencentcloudapi.com")
                .addHeader("Authorization", authorization)
                .addHeader("X-TC-Action", "SentenceRecognition")
                .addHeader("X-TC-Version", API_VERSION)
                .addHeader("X-TC-Timestamp", timestamp)
                .addHeader("X-TC-Region", "ap-shanghai")
                .post(RequestBody.create(JSON, requestBody))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            JsonNode jsonNode = objectMapper.readTree(responseBody);

            // 检查是否有错误
            if (jsonNode.has("Response") && jsonNode.get("Response").has("Error")) {
                JsonNode error = jsonNode.get("Response").get("Error");
                String errorCode = error.get("Code").asText();
                String errorMessage = error.get("Message").asText();
                throw new IOException("API返回错误: " + errorCode + ": " + errorMessage);
            }

            // 提取识别结果
            if (jsonNode.has("Response") && jsonNode.get("Response").has("Result")) {
                return jsonNode.get("Response").get("Result").asText();
            } else {
                logger.warn("响应中没有识别结果: {}", responseBody);
                return "";
            }
        }
    }

    /**
     * 计算字符串的SHA256哈希值
     */
    private String sha256Hex(String data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    /**
     * 计算HMAC-SHA256
     */
    private byte[] hmacSha256(String key, String data) throws NoSuchAlgorithmException, InvalidKeyException {
        return hmacSha256(key.getBytes(StandardCharsets.UTF_8), data);
    }

    /**
     * 计算HMAC-SHA256
     */
    private byte[] hmacSha256(byte[] key, String data) throws NoSuchAlgorithmException, InvalidKeyException {
        String algorithm = "HmacSHA256";
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(key, algorithm));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
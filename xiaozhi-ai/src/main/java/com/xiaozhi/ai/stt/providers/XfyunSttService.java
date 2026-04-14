package com.xiaozhi.ai.stt.providers;

import cn.xfyun.model.response.iat.IatResponse;
import cn.xfyun.model.response.iat.Text;
import com.google.gson.JsonObject;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.ai.utils.HttpUtil;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static cn.xfyun.util.StringUtils.gson;

public class XfyunSttService implements SttService {
    private static final Logger logger = LoggerFactory.getLogger(XfyunSttService.class);

    public static final int StatusFirstFrame = 0;
    public static final int StatusContinueFrame = 1;
    public static final int StatusLastFrame = 2;

    private static final String PROVIDER_NAME = "xfyun";

    // 识别超时时间（90秒）
    private static final long RECOGNITION_TIMEOUT_MS = 90000;

    private static final String hostUrl = "https://iat-api.xfyun.cn/v2/iat";

    private String secretId;
    private String secretKey;
    private String appId;

    public XfyunSttService(ConfigBO config) {
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


    /**
     * 处理返回结果（包括全量返回与流式返回（结果修正））
     */
    private void handleResultText(Text textObject, List<Text> resultSegments) {
        // 处理流式返回的替换结果
        if ("rpl".equals(textObject.getPgs()) && textObject.getRg() != null && textObject.getRg().length == 2) {
            // 返回结果序号sn字段的最小值为1
            int start = textObject.getRg()[0] - 1;
            int end = textObject.getRg()[1] - 1;

            // 将指定区间的结果设置为删除状态
            for (int i = start; i <= end && i < resultSegments.size(); i++) {
                resultSegments.get(i).setDeleted(true);
            }
            // logger.info("替换操作，服务端返回结果为：" + textObject);
        }

        // 通用逻辑，添加当前文本到结果列表
        resultSegments.add(textObject);
    }

    /**
     * 获取最终结果
     */
    private String getFinalResult(List<Text> resultSegments) {
        StringBuilder finalResult = new StringBuilder();
        for (Text text : resultSegments) {
            if (text != null && !text.isDeleted()) {
                finalResult.append(text.getText());
            }
        }
        return finalResult.toString();
    }

    private String getAuthUrl(String apiKey, String apiSecret) throws Exception {
        URL url = URI.create(XfyunSttService.hostUrl).toURL();
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());

        StringBuilder builder = new StringBuilder("host: ").append(url.getHost()).append("\n")
                .append("date: ").append(date).append("\n")
                .append("GET ").append(url.getPath()).append(" HTTP/1.1");

        Charset charset = StandardCharsets.UTF_8;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(charset), "HmacSHA256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(builder.toString().getBytes(charset));
        String sha = Base64.getEncoder().encodeToString(hexDigits);

        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"",
                apiKey, "hmac-sha256", "host date request-line", sha);

        return Objects.requireNonNull(HttpUrl.parse("https://" + url.getHost() + url.getPath()))
                .newBuilder()
                .addQueryParameter("authorization",
                        Base64.getEncoder().encodeToString(authorization.getBytes(charset)))
                .addQueryParameter("date", date)
                .addQueryParameter("host", url.getHost())
                .build()
                .toString();
    }

    @Override
    public SttResult stream(Flux<byte[]> audioSink) {
        // 检查配置是否已设置
        if (secretId == null || secretKey == null || appId == null) {
            logger.error("讯飞云语音识别配置未设置，无法进行识别");
            return null;
        }

        // 构建鉴权URL
        String authUrl;
        try {
            authUrl = getAuthUrl(secretId, secretKey);
        } catch (Exception e) {
            logger.error("构建鉴权URL时发生错误！", e);
            return SttResult.textOnly("");
        }

        String wsUrl = authUrl.replace("http://", "ws://")
                .replace("https://", "wss://");
        Request request = new Request.Builder().url(wsUrl).build();
        AtomicInteger status = new AtomicInteger(StatusFirstFrame);
        AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();
        BlockingQueue<JsonObject> frameQueue = new LinkedBlockingQueue<>();
        AtomicBoolean isClosed = new AtomicBoolean(false);
        AtomicBoolean latchReleased = new AtomicBoolean(false);
        CountDownLatch recognitionLatch = new CountDownLatch(1);
        List<Text> resultSegments = new ArrayList<>();

        HttpUtil.client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocketRef.set(webSocket);
                isClosed.set(false);
                // 使用 Flux 订阅音频流
                audioSink.subscribeOn(Schedulers.single())  // 保证顺序执行
                        .subscribe(
                                chunk -> {
                                    if (isClosed.get()) return;
                                    try {
                                        if (chunk == null || chunk.length == 0) {
                                            logger.debug("audioSink 数据为空，跳过此帧");
                                            return;
                                        }
                                        if ((status.compareAndSet(StatusFirstFrame, StatusContinueFrame))) {
                                            logger.debug("xfyun开始发送音频首帧");
                                            frameQueue.offer(buildFirstFrame(chunk, chunk.length));
                                        } else {
                                            // logger.debug("xfyun继续发送音频帧");
                                            frameQueue.offer(buildContinueFrame(chunk, chunk.length));
                                        }
                                    } catch (Exception e) {
                                        logger.error("发送音频帧失败", e);
                                    }
                                },
                                error -> {
                                    logger.error("音频流错误", error);
                                },
                                () -> {
                                    if (isClosed.get()) return;
                                    // 流结束，通过队列发送最后一帧，保证帧顺序
                                    logger.debug("audioSink结束发送结束通知");
                                    JsonObject frame = buildLastFrame();
                                    frameQueue.offer(frame);
                                }
                        );
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (isClosed.get()) return;
                IatResponse response = gson.fromJson(text, IatResponse.class);
                if (response.getCode() != 0) {
                    logger.warn("code:{}, error:{}, sid:{}",
                            response.getCode(), response.getMessage(), response.getSid());
                    return;
                }

                if (response.getData() != null && response.getData().getResult() != null) {
                    Text textObject = response.getData().getResult().getText();
                    handleResultText(textObject, resultSegments);
                }

                if (response.getData() != null && response.getData().getStatus() == 2) {
                    if (latchReleased.compareAndSet(false, true)) {
                        recognitionLatch.countDown();
                    }
                    // wsClose();
                    wsClose(webSocketRef, isClosed); // 显式关闭
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("流式识别失败", t);
                wsClose(webSocketRef, isClosed); // 显式关闭
                isClosed.set(true);
                webSocketRef.set(null);
                if (latchReleased.compareAndSet(false, true)) {
                    recognitionLatch.countDown();
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                wsClose(webSocketRef, isClosed); // 显式关闭
                isClosed.set(true);
                webSocketRef.set(null);
                super.onClosed(webSocket, code, reason);
            }
        });

        // 发送帧线程
        Thread.startVirtualThread(() -> {
            while (!isClosed.get()) {
                try {
                    JsonObject frame = frameQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (frame != null) {
                        WebSocket ws = webSocketRef.get();
                        if (ws != null) {
                            ws.send(frame.toString());
                        }
                    }
                } catch (Exception e) {
                    logger.error("发送音频帧失败", e);
                }
            }
        });

        try {
            // 等待识别完成或超时
            boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            String finalText = "";
            if (recognized) {
                finalText = getFinalResult(resultSegments);
            } else {
                String partialResult = getFinalResult(resultSegments);
                // 即使超时也返回已识别的部分文本
                if (StringUtils.hasText(partialResult)) {
                    finalText = partialResult;
                }
                wsClose(webSocketRef, isClosed);
            }
            return SttResult.textOnly(finalText);
        } catch (Exception e) {
            logger.error("创建语音识别会话时发生错误", e);
            wsClose(webSocketRef, isClosed);
            // 主动关闭会话
            return SttResult.textOnly(getFinalResult(resultSegments));
        }
    }

    private void wsClose(AtomicReference<WebSocket> webSocketRef, AtomicBoolean isClosed) {
        if (isClosed.compareAndSet(false, true)) {
            WebSocket ws = webSocketRef.get();
            if (ws != null) {
                try {
                    ws.close(1000, "程序关闭");
                } catch (Exception e) {
                    logger.warn("关闭 WebSocket 时发生异常", e);
                }
            }
        }
    }

    private JsonObject buildFirstFrame(byte[] buffer, int len) {
        JsonObject common = new JsonObject();
        common.addProperty("app_id", appId);

        JsonObject business = new JsonObject();
        business.addProperty("language", "zh_cn");
        business.addProperty("domain", "iat");
        business.addProperty("accent", "mandarin");
        business.addProperty("dwa", "wpgs");

        JsonObject data = new JsonObject();
        data.addProperty("status", StatusFirstFrame);
        data.addProperty("format", "audio/L16;rate=16000");
        data.addProperty("encoding", "raw");
        data.addProperty("audio", Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, len)));

        JsonObject frame = new JsonObject();
        frame.add("common", common);
        frame.add("business", business);
        frame.add("data", data);

        return frame;
    }

    private JsonObject buildContinueFrame(byte[] buffer, int len) {
        JsonObject data = new JsonObject();
        data.addProperty("status", StatusContinueFrame);
        data.addProperty("format", "audio/L16;rate=16000");
        data.addProperty("encoding", "raw");
        data.addProperty("audio", Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, len)));

        JsonObject frame = new JsonObject();
        frame.add("data", data);

        return frame;
    }

    private JsonObject buildLastFrame() {
        JsonObject data = new JsonObject();
        data.addProperty("status", StatusLastFrame);
        data.addProperty("audio", "");
        data.addProperty("format", "audio/L16;rate=16000");
        data.addProperty("encoding", "raw");

        JsonObject frame = new JsonObject();
        frame.add("data", data);

        return frame;
    }
}
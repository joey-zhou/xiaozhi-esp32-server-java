package com.xiaozhi.dialogue.stt.providers;

import cn.xfyun.api.IatClient;
import cn.xfyun.model.response.iat.IatResponse;
import cn.xfyun.model.response.iat.IatResult;
import cn.xfyun.model.response.iat.Text;
import cn.xfyun.service.iat.AbstractIatWebSocketListener;
import com.google.gson.JsonObject;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
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

    // 识别超时时间（5秒）
    private static final long RECOGNITION_TIMEOUT_MS = 5000;

    private static final String hostUrl = "https://iat-api.xfyun.cn/v2/iat";

    private String secretId;
    private String secretKey;
    private String appId;

    public XfyunSttService(SysConfig config) {
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
        List<Text> resultSegments = new ArrayList<>();
        // 将原始音频数据转换为MP3格式并保存（用于调试）
        String fileName = AudioUtils.saveAsWav(audioData);
        File file = new File(fileName);
        CountDownLatch recognitionLatch = new CountDownLatch(1);
        try {
            // 检查配置是否已设置
            if (secretId == null || secretKey == null) {
                logger.error("讯飞云语音识别配置未设置，无法进行识别");
                return null;
            }

            // 设置听写参数,这里的appid,apiKey,apiSecret是在开放平台控制台获得
            IatClient iatClient = new IatClient.Builder()
                    .signature(appId, secretId, secretKey)
                    // 动态修正功能：值为wpgs时代表开启（包含修正功能的）流式听写
                    .dwa("wpgs")
                    .build();

            iatClient.send(file, new AbstractIatWebSocketListener() {
                @Override
                public void onSuccess(WebSocket webSocket, IatResponse iatResponse) {
                    if (iatResponse.getCode() != 0) {
                        logger.warn("code：{}, error：{}, sid：{}", iatResponse.getCode(), iatResponse.getMessage(), iatResponse.getSid());
                        logger.warn("错误码查询链接：https://www.xfyun.cn/document/error-code");
                        return;
                    }

                    if (iatResponse.getData() != null) {
                        if (iatResponse.getData().getResult() != null) {
                            // 解析服务端返回结果
                            IatResult result = iatResponse.getData().getResult();
                            Text textObject = result.getText();
                            handleResultText(textObject, resultSegments);
                            logger.info("中间识别结果：{}", getFinalResult(resultSegments));
                        }

                        if (iatResponse.getData().getStatus() == 2) {
                            // resp.data.status ==2 说明数据全部返回完毕，可以关闭连接，释放资源
                            logger.info("session end ");
                            iatClient.closeWebsocket();
                            recognitionLatch.countDown();
                        } else {
                            // 根据返回的数据自定义处理逻辑
                        }
                    }
                }

                @Override
                public void onFail(WebSocket webSocket, Throwable t, Response response) {
                    // 自定义处理逻辑
                    recognitionLatch.countDown();
                }
            });
            // 等待识别完成或超时
            boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!recognized) {
                logger.warn("讯飞云识别超时");
            }
            return getFinalResult(resultSegments);
        } catch (Exception e) {
            logger.error("处理音频时发生错误！", e);
            return null;
        }
    }

    /**
     * 处理返回结果（包括全量返回与流式返回（结果修正））
     */
    private void handleResultText(Text textObject, List<Text> resultSegments) {
        // 处理流式返回的替换结果
        if (StringUtils.equals(textObject.getPgs(), "rpl") && textObject.getRg() != null && textObject.getRg().length == 2) {
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
        URL url = new URL(XfyunSttService.hostUrl);
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
    public String streamRecognition(Sinks.Many<byte[]> audioSink) {
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
            return null;
        }

        String wsUrl = authUrl.replace("http://", "ws://")
                .replace("https://", "wss://");
        OkHttpClient client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder().url(wsUrl).build();
        AtomicInteger status = new AtomicInteger(StatusFirstFrame);
        AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();
        BlockingQueue<JsonObject> frameQueue = new LinkedBlockingQueue<>();
        AtomicBoolean isClosed = new AtomicBoolean(false);
        AtomicBoolean latchReleased = new AtomicBoolean(false);
        CountDownLatch recognitionLatch = new CountDownLatch(1);
        List<Text> resultSegments = new ArrayList<>();

        WebSocket webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocketRef.set(webSocket);
                isClosed.set(false);
                Thread.startVirtualThread(() -> {
                    // 使用 Flux 订阅音频流
                    audioSink.asFlux()
                            .subscribeOn(Schedulers.single())  // 保证顺序执行
                            .subscribe(
                                    chunk -> {
                                        if (isClosed.get()) return;
                                        try {
                                            if (chunk == null || chunk.length == 0) {
                                                logger.debug("audioSink 数据为空，主动结束流");
                                                frameQueue.offer(buildContinueFrame(chunk, chunk.length));
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
                                        // 流结束，发送最后一帧
                                        logger.debug("audioSink结束发送结束通知");
                                        JsonObject frame = buildLastFrame();
                                        webSocket.send(frame.toString());
                                    }
                            );
                });
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
                    logger.info("onMessage中间识别结果：{}", getFinalResult(resultSegments));
                }

                if (response.getData() != null && response.getData().getStatus() == 2) {
                    logger.info("onMessage is finish ");
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
        Thread sendThread = new Thread(() -> {
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
        sendThread.start();

        try {
            // 等待识别完成或超时
            boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            String finalText = "";
            if (recognized) {
                finalText = getFinalResult(resultSegments);
                logger.info("最终识别结果：{}", finalText);
            } else {
                logger.warn("讯飞云识别超时！");
                wsClose(webSocketRef, isClosed);
            }
            return finalText;
        } catch (Exception e) {
            logger.error("创建语音识别会话时发生错误", e);
            wsClose(webSocketRef, isClosed);
            // 主动关闭会话
            return getFinalResult(resultSegments);
        }
    }

    private void wsClose(AtomicReference<WebSocket> webSocketRef, AtomicBoolean isClosed) {
        if (isClosed.compareAndSet(false, true)) {
            WebSocket ws = webSocketRef.get();
            if (ws != null) {
                try {
                    logger.info("xfyun wsClose");
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
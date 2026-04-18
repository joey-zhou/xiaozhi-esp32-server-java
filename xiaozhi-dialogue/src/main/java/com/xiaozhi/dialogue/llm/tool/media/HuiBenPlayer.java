package com.xiaozhi.dialogue.llm.tool.media;

import com.xiaozhi.common.Speech;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.utils.AudioUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HuiBenPlayer  {

    private static final String API_BASE_URL = "https://www.limaogushi.com/huiben/";

    // 使用OkHttp3替代JDK HttpClient
    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Integer bookId;
    private final ChatSession session;

    public HuiBenPlayer(ChatSession session, Integer bookId) {
        this.session = session;
        this.bookId = bookId;
    }

    /**
     * 播放绘本
     * 使用JDK 21虚拟线程和结构化并发实现异步处理
     */
    public void play() {

        // 1. 获取绘本信息
        Map<String, String> huiBenInfo = getHuiBenInfo(bookId);
        if (huiBenInfo == null) {
            throw new RuntimeException("无法找到绘本: " + bookId);
        }

        // 2. 下载音频文件到本地临时目录，使用随机文件名避免冲突
        String audioUrl = huiBenInfo.get("audioUrl");
        String randomName = "huiBen_" + session.getSessionId() + "_" + UUID.randomUUID() + ".mp3";
        // 读取音频文件
        Path audioFilePath = downloadFile(audioUrl, randomName);

        if (audioFilePath == null || !Files.exists(audioFilePath)) {
            log.warn("音频文件不存在: {}", audioFilePath);
            return;
        }

        // 发送绘本开始消息

        // 发送音频和同步文本
        try {
            // 将音频文件转换为PCM格式
            byte[] audioData = AudioUtils.readAsPcm(audioFilePath.toAbsolutePath().toString());
            if (audioData == null || audioData.length == 0) {
                log.warn("音频数据为空");
                return;
            }
            Flux<Speech> speechFlux = Flux.just(new Speech(audioData))
                    .doFinally(signalType -> AudioUtils.deleteFile(audioFilePath.toString()));
            session.getPlayer().play(speechFlux);

        } catch (Exception e) {
            log.error("处理音频时发生错误 ", e);
        }
    }

    /**
     * 获取绘本信息（音频URL）
     */
    private Map<String, String> getHuiBenInfo(Integer bookId) {
        try {
            // 构建URL
            String url = API_BASE_URL + bookId + ".html";

            // 使用OkHttp3发送请求
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("获取绘本信息失败，响应码: {}", response.code());
                    return null;
                }

                // 解析响应
                String responseBody = response.body() != null ? response.body().string() : null;
                if (responseBody == null) {
                    log.error("获取绘本信息失败，响应体为空");
                    return null;
                }

                String audioUrl = extractAudioSrcByRegex(responseBody);
                Map<String, String> result = new HashMap<>();
                result.put("audioUrl", audioUrl);
                return result;
            }
        } catch (Exception e) {
            log.error("获取绘本信息时发生错误", e);
            return null;
        }
    }

    /**
     * 从HTML中提取音频源URL
     */
    public static String extractAudioSrcByRegex(String html) {
        // 匹配 source 标签中的 src 属性
        Pattern pattern = Pattern.compile("<source\\s+[^>]*src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>");
        Matcher matcher = pattern.matcher(html);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 下载文件到临时目录
     */
    private Path downloadFile(String fileUrl, String fileName) {
        try {
            // 确保音频目录存在
            Path audioDir = Path.of(AudioUtils.AUDIO_PATH);
            Files.createDirectories(audioDir);

            // 将文件保存到音频目录
            Path outputPath = audioDir.resolve(fileName);

            // 使用OkHttp3下载文件
            Request request = new Request.Builder()
                    .url(fileUrl)
                    .get()
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("下载文件失败，响应码: {}", response.code());
                    return null;
                }

                // 将响应体写入文件
                Files.write(outputPath, response.body().bytes());

                //return outputPath.toAbsolutePath().toString();
                return outputPath;
            }
        } catch (Exception e) {
            log.error("下载文件时发生错误", e);
            return null;
        }
    }
}

package com.xiaozhi.dialogue.llm.tool.media;

import com.xiaozhi.common.Speech;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.utils.AudioUtils;
import io.jsonwebtoken.lang.Assert;
import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MusicPlayer {
    private static final String API_BASE_URL = "";//"https://api.xiaozhi.com/api/v1/music/search";
    // 使用OkHttp3替代JDK HttpClient
    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final Logger logger = LoggerFactory.getLogger(MusicPlayer.class);

    private final ChatSession session;

    @Getter
    private final String song;
    @Getter
    private final String artist;

    // 存储每个会话的当前歌词信息
    @Getter
    private List<LyricLine> lyrics = new ArrayList<>();

    // 存储音频文件路径，用于播放完成后删除
    @Getter
    private Path audioFile;

    public MusicPlayer(ChatSession session, String song, String artist) {
        Assert.notNull(session.getPlayer(), "当前会话未初始化播放器");
        this.session = session;
        this.song = song;
        this.artist = artist;

    }


    /**
     * 搜索并播放音乐
     * 调用者使用虚拟线程异步处理
     */
    public void play() {
        String sessionId = session.getSessionId();
        try {

            // 清理之前的音频文件（如果有）
            AudioUtils.deleteFile(audioFile.toString());

            // 1. 获取音乐信息
            Map<String, String> musicInfo = getMusicInfo(song, artist);
            if (musicInfo == null) {
                throw new RuntimeException("无法找到歌曲: " + song + (artist != null ? " - " + artist : ""));
            }

            // 2. 下载音频文件到本地临时目录，使用随机文件名避免冲突
            String audioUrl = musicInfo.get("audioUrl");
            String randomName = "music_" + sessionId + "_" + UUID.randomUUID() + ".mp3";
            audioFile = downloadFile(audioUrl, randomName);

            if (!Files.exists(audioFile)) {
                throw new RuntimeException("下载音频文件失败");
            }

            // 3. 解析歌词
            String lyricUrl = musicInfo.get("lyricUrl");
            lyrics = parseLyrics(lyricUrl);

            // 发送音频和同步歌词
            sendAudioWithLyrics(audioFile);

        } catch (Exception e) {
            logger.error("播放音乐时发生错误", e);
            session.getPersona().getSynthesizer().synthesize("播放音乐时发生错误");
        }

    }


    /**
     * 发送音频和同步歌词
     */
    private void sendAudioWithLyrics(Path audioPath) {

        if (!Files.exists(audioPath)) {
            logger.error("音频文件不存在: {}", audioPath);
            return;
        }
        if (CollectionUtils.isEmpty(lyrics) || lyrics.size() < 2) {
            session.getPlayer().play(song, audioPath);
            return;
        }
        try {
            // 读取音频文件
            // 将音频文件转换为PCM格式
            byte[] audioData = AudioUtils.readAsPcm(audioPath.toAbsolutePath().toString());
            if (audioData == null || audioData.length == 0) {
                logger.warn("音频数据为空");
                return;
            }


            // 预处理歌词时间点，将毫秒时间转换为帧索引
            // 1个Opus帧大约为几百字节，60ms，而pcm字节数也是与时间长度正相关的。 可以按毫秒数大约估算对应的字节长度。
            LyricLine[] lines=lyrics.toArray(new LyricLine[0]);

            // 通过音频元数据获取歌曲真实时长
            double durationSec = AudioUtils.getAudioDuration(audioPath);
            long sumMs = durationSec > 0 ? (long)(durationSec * 1000) : lines[1].timeMs() + lines[lines.length - 1].timeMs();
            // 平均每个毫秒的字节数
            int avg = (int) ( audioData.length / sumMs);
            int startIndex=0;
            int endIndex=0;
            List<Speech> speeches = new ArrayList<>();
            for (int i=0;i<lines.length-1;i++) {
                // 计算歌词对应的音频字节数组
                int ms = lines[i+1].timeMs()-lines[i].timeMs();
                endIndex = ms * avg;
                if(endIndex>audioData.length){

                    logger.warn("歌词超出音频长度，已截断");
                    break;
                }
                byte[] frameData = Arrays.copyOfRange(audioData, startIndex, endIndex);
                String text = lines[i].text();
                Speech speech = new Speech(frameData, text);
                speeches.add(speech);
                startIndex = endIndex;
            }
            if(endIndex<audioData.length){
                byte[] frameData = Arrays.copyOfRange(audioData, startIndex, audioData.length);
                Speech speech = new Speech(frameData, lines[lines.length-1].text());
                speeches.add(speech);
            }
            session.getPlayer().play(Flux.fromIterable( speeches));

        } catch (Exception e) {
            String sessionId = session.getSessionId();
            logger.error("处理音频时发生错误 - SessionId: {}", sessionId, e);

        }
    }

    /**
     * 获取音乐信息（音频URL和歌词URL）
     */
    private Map<String, String> getMusicInfo(String song, String artist) {
        try {
            // 构建URL
            StringBuilder urlBuilder = new StringBuilder(API_BASE_URL + "/stream_pcm?song=" +
                    URLEncoder.encode(song, StandardCharsets.UTF_8));

            if (artist != null && !artist.isEmpty()) {
                urlBuilder.append("&artist=").append(URLEncoder.encode(artist, StandardCharsets.UTF_8));
            }

            // 使用OkHttp3发送请求
            Request request = new Request.Builder()
                    .url(urlBuilder.toString())
                    .get()
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("获取音乐信息失败，响应码: {}", response.code());
                    return null;
                }

                // 解析JSON响应
                String responseBody = response.body() != null ? response.body().string() : null;
                if (responseBody == null) {
                    logger.error("获取音乐信息失败，响应体为空");
                    return null;
                }

                Map<String, Object> responseMap = new ObjectMapper().readValue(responseBody, Map.class);

                Map<String, String> result = new HashMap<>();

                // 检查API响应格式，支持两种可能的字段名
                String audioPath = (String) responseMap.get("audioPath");
                String audioUrl = (String) responseMap.get("audio_url");

                String lyricPath = (String) responseMap.get("lyricPath");
                String lyricUrl = (String) responseMap.get("lyric_url");

                // 优先使用直接URL，否则构建URL
                if (audioUrl != null && !audioUrl.isEmpty()) {
                    if (!audioUrl.startsWith("http")) {
                        audioUrl = API_BASE_URL + audioUrl;
                    }
                    result.put("audioUrl", audioUrl);
                } else if (audioPath != null && !audioPath.isEmpty()) {
                    result.put("audioUrl", API_BASE_URL + "/get_file?path=" +
                            URLEncoder.encode(audioPath, StandardCharsets.UTF_8) +
                            "&name=" + URLEncoder.encode(song + ".mp3", StandardCharsets.UTF_8));
                } else {
                    logger.error("API响应中缺少音频URL信息");
                    return null;
                }

                if (lyricUrl != null && !lyricUrl.isEmpty()) {
                    if (!lyricUrl.startsWith("http")) {
                        lyricUrl = API_BASE_URL + lyricUrl;
                    }
                    result.put("lyricUrl", lyricUrl);
                } else if (lyricPath != null && !lyricPath.isEmpty()) {
                    result.put("lyricUrl", API_BASE_URL + "/get_file?path=" +
                            URLEncoder.encode(lyricPath, StandardCharsets.UTF_8) +
                            "&name=" + URLEncoder.encode(song + ".lrc", StandardCharsets.UTF_8));
                } else {
                    // 歌词可选，没有歌词也可以播放
                    logger.warn("API响应中缺少歌词URL信息");
                }

                return result;
            }
        } catch (Exception e) {
            logger.error("获取音乐信息时发生错误", e);
            return null;
        }
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
                    logger.error("下载文件失败，响应码: {}", response.code());
                    return null;
                }

                // 将响应体写入文件
                Files.write(outputPath, response.body().bytes());
                logger.info("文件已保存到: {}", outputPath);

                return outputPath;
            }
        } catch (Exception e) {
            logger.error("下载文件时发生错误", e);
            return null;
        }
    }

    /**
     * 解析LRC格式歌词
     */
    private List<LyricLine> parseLyrics(String lyricUrl) {
        List<LyricLine> result = new ArrayList<>();

        if (lyricUrl == null || lyricUrl.isEmpty()) {
            logger.warn("歌词URL为空，无法解析歌词");
            return result;
        }

        try {

            // 使用OkHttp3发送请求
            Request request = new Request.Builder()
                    .url(lyricUrl)
                    .get()
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    logger.error("获取歌词失败，响应码: {}", response.code());
                    return result;
                }

                String responseBody = response.body().string();

                // LRC时间标签正则表达式: [mm:ss.xx]
                Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)");

                // 使用Stream API处理每一行
                return responseBody.lines()
                        .map(pattern::matcher)
                        .filter(Matcher::find)
                        .map(matcher -> {
                            int minutes = Integer.parseInt(matcher.group(1));
                            int seconds = Integer.parseInt(matcher.group(2));
                            int hundredths = Integer.parseInt(matcher.group(3));

                            // 计算毫秒时间
                            int timeMs = (minutes * 60 * 1000) + (seconds * 1000) + (hundredths * 10);
                            String text = matcher.group(4).trim();

                            return new LyricLine(timeMs, text);
                        })
                        .sorted(Comparator.comparingLong(LyricLine::timeMs))
                        .toList();
            }

        } catch (Exception e) {
            logger.error("解析歌词时发生错误", e);
        }

        return result;
    }

}

/**
 * 歌词行数据结构 - 使用JDK 16+ Record类型
 */
record LyricLine(int timeMs, String text) {
}

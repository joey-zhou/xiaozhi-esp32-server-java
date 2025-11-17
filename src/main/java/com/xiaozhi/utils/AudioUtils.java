package com.xiaozhi.utils;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FrameRecorder;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AudioUtils {
    public static final String AUDIO_PATH = "audio/";
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(AudioUtils.class);
    public static final int FRAME_SIZE = 960;
    public static final int SAMPLE_RATE = 16000; // 采样率
    public static final int CHANNELS = 1; // 单声道
    public static final int BITRATE = 16000; // 16kbps比特率
    public static final int SAMPLE_FORMAT = avutil.AV_SAMPLE_FMT_S16; // 16位PCM
    public static final int BUFFER_SIZE = 512; // 窗口大小
    public static final int OPUS_FRAME_DURATION_MS = 60; // OPUS帧持续时间（毫秒）

    /**
     * 将原始音频数据保存为MP3文件
     *
     * @param audio PCM音频数据
     * @return 文件名
     */
    public static String saveAsMp3(byte[] audio) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileName = uuid + ".mp3";
        String filePath = AUDIO_PATH + fileName;

        // 创建临时PCM文件
        String tempPcmPath = AUDIO_PATH + uuid + ".pcm";

        try {
            // 确保音频目录存在
            Files.createDirectories(Paths.get(AUDIO_PATH));

            // 先将PCM数据写入临时文件
            try (FileOutputStream fos = new FileOutputStream(tempPcmPath)) {
                fos.write(audio);
            }

            // 构建ffmpeg命令：将PCM转换为MP3
            String[] command = {
                    "ffmpeg",
                    "-f", "s16le", // 输入格式：16位有符号小端序PCM
                    "-ar", String.valueOf(SAMPLE_RATE), // 采样率
                    "-ac", String.valueOf(CHANNELS), // 声道数
                    "-i", tempPcmPath, // 输入文件
                    "-b:a", String.valueOf(BITRATE), // 比特率
                    "-f", "mp3", // 输出格式
                    "-q:a", "0", // 最高质量
                    filePath // 输出文件
            };

            // 执行命令
            Process process = Runtime.getRuntime().exec(command);

            // 读取错误输出以便调试
            StringBuilder errorOutput = new StringBuilder();
            try (InputStream errorStream = process.getErrorStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = errorStream.read(buffer)) != -1) {
                    errorOutput.append(new String(buffer, 0, bytesRead));
                }
            }

            // 等待进程完成
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("ffmpeg转换失败，退出代码: {}，错误信息: {}", exitCode, errorOutput.toString());
                return null;
            }

            // 检查输出文件是否存在
            if (!Files.exists(Paths.get(filePath))) {
                logger.error("ffmpeg转换后的MP3文件不存在");
                return null;
            }

            return fileName;
        } catch (IOException | InterruptedException e) {
            logger.error("保存MP3文件时发生错误", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            // 删除临时PCM文件
            try {
                Files.deleteIfExists(Paths.get(tempPcmPath));
            } catch (IOException e) {
                logger.warn("删除临时PCM文件失败", e);
            }
        }
    }

    public static String saveAsWav(byte[] audio) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileName = uuid + ".wav";
        Path path = Path.of(AUDIO_PATH , fileName);
        saveAsWav(path, audio);
        return fileName;
    }
    /**
     * 将原始音频数据保存为WAV文件
     *
     * @param audioData 音频数据
     * @return 文件名
     */
    public static void saveAsWav(Path path, byte[] audioData) {

        // WAV文件参数
        int bitsPerSample = 16; // 16位采样

        try {
            // 确保音频目录存在
            Files.createDirectories(path.getParent());

            try (FileOutputStream fos = new FileOutputStream(path.toFile());
                 DataOutputStream dos = new DataOutputStream(fos)) {

                // 写入WAV文件头
                // RIFF头
                dos.writeBytes("RIFF");
                dos.writeInt(Integer.reverseBytes(36 + audioData.length)); // 文件长度
                dos.writeBytes("WAVE");

                // fmt子块
                dos.writeBytes("fmt ");
                dos.writeInt(Integer.reverseBytes(16)); // 子块大小
                dos.writeShort(Short.reverseBytes((short) 1)); // 音频格式 (1 = PCM)
                dos.writeShort(Short.reverseBytes((short) CHANNELS)); // 通道数
                dos.writeInt(Integer.reverseBytes(SAMPLE_RATE)); // 采样率
                dos.writeInt(Integer.reverseBytes(SAMPLE_RATE * CHANNELS * bitsPerSample / 8)); // 字节率
                dos.writeShort(Short.reverseBytes((short) (CHANNELS * bitsPerSample / 8))); // 块对齐
                dos.writeShort(Short.reverseBytes((short) bitsPerSample)); // 每个样本的位数

                // data子块
                dos.writeBytes("data");
                dos.writeInt(Integer.reverseBytes(audioData.length)); // 数据大小

                // 写入音频数据
                dos.write(audioData);
            }
        } catch (FrameRecorder.Exception e) {
            logger.error("编码WAV时发生错误", e);
        } catch (IOException e) {
            logger.error("写入WAV文件时发生错误", e);
        }
    }

    /**
     * 合并多个音频文件为一个WAV文件
     * 支持合并的格式： wav, mp3, pcm
     *
     * @param audioPaths 要合并的音频文件路径列表
     * @return 合并后的WAV文件名
     */
    public static void mergeAudioFiles(Path path, List<String> audioPaths) {
        if (audioPaths.size() == 1) {
            // TODO 考虑改为文件迁移mv 或rename
//            return Paths.get(audioPaths.getFirst()).getFileName().toString();
        }
//        var uuid = UUID.randomUUID().toString().replace("-", "");
//        var outputFileName = uuid + ".wav";
//        var outputPath = Paths.get(AUDIO_PATH, outputFileName).toString();

        try {
            // 确保音频目录存在
            Files.createDirectories(path.getParent());
            // 计算所有PCM数据的总大小
            var totalPcmSize = 0L;
            var audioChunks = new ArrayList<byte[]>();
            for (var audioPath : audioPaths) {
                var fullPath = audioPath.startsWith(AUDIO_PATH) ? audioPath : AUDIO_PATH + audioPath;
                byte[] pcmData = readAsPcm(fullPath);
                totalPcmSize += pcmData.length;
                audioChunks.add(pcmData);
            }

            // 创建输出WAV文件
            try (FileOutputStream fos = new FileOutputStream(path.toFile());
                 DataOutputStream dos = new DataOutputStream(fos)) {

                // 写入WAV文件头
                int bitsPerSample = 16; // 16位采样

                // RIFF头
                dos.writeBytes("RIFF");
                dos.writeInt(Integer.reverseBytes(36 + (int) totalPcmSize)); // 文件长度
                dos.writeBytes("WAVE");

                // fmt子块
                dos.writeBytes("fmt ");
                dos.writeInt(Integer.reverseBytes(16)); // 子块大小
                dos.writeShort(Short.reverseBytes((short) 1)); // 音频格式 (1 = PCM)
                dos.writeShort(Short.reverseBytes((short) CHANNELS)); // 通道数
                dos.writeInt(Integer.reverseBytes(SAMPLE_RATE)); // 采样率
                dos.writeInt(Integer.reverseBytes(SAMPLE_RATE * CHANNELS * bitsPerSample / 8)); // 字节率
                dos.writeShort(Short.reverseBytes((short) (CHANNELS * bitsPerSample / 8))); // 块对齐
                dos.writeShort(Short.reverseBytes((short) bitsPerSample)); // 每个样本的位数

                // data子块
                dos.writeBytes("data");
                dos.writeInt(Integer.reverseBytes((int) totalPcmSize)); // 数据大小

                // 依次写入每个文件的PCM数据
                for (var pcmData : audioChunks) {
                    dos.write(pcmData);
                }
            }
            // 目前采用的处理策略是删除已经合并了的文件。
            for (var audioPath : audioPaths) {
                var fullPath = audioPath.startsWith(AUDIO_PATH) ? audioPath : AUDIO_PATH + audioPath;
                Files.deleteIfExists(Paths.get(fullPath));
            }

        } catch (Exception e) {
            logger.error("合并音频文件时发生错误", e);
        }
    }

    /**
     * 从WAV文件中提取PCM数据
     *
     * @param wavPath WAV文件路径
     * @return PCM数据字节数组
     */
    public static byte[] wavToPcm(String wavPath) throws IOException {
        // 读取整个文件
        byte[] wavData = Files.readAllBytes(Paths.get(wavPath));
        return wavBytesToPcm(wavData);
    }

    /**
     * 从WAV字节数据中提取PCM数据
     *
     * @param wavData WAV文件的字节数据
     * @return PCM数据字节数组
     */
    public static byte[] wavBytesToPcm(byte[] wavData) throws IOException {
        if (wavData == null || wavData.length < 44) { // WAV头至少44字节
            throw new IOException("无效的WAV数据");
        }

        // 检查WAV文件标识
        if (wavData[0] != 'R' || wavData[1] != 'I' || wavData[2] != 'F' || wavData[3] != 'F' ||
                wavData[8] != 'W' || wavData[9] != 'A' || wavData[10] != 'V' || wavData[11] != 'E') {
            throw new IOException("不是有效的WAV文件格式");
        }

        // 查找data子块
        int dataOffset = -1;
        for (int i = 12; i < wavData.length - 4; i++) {
            if (wavData[i] == 'd' && wavData[i + 1] == 'a' && wavData[i + 2] == 't' && wavData[i + 3] == 'a') {
                dataOffset = i + 8; // 跳过"data"和数据大小字段
                break;
            }
        }

        if (dataOffset == -1) {
            throw new IOException("在WAV文件中找不到data子块");
        }

        // 计算PCM数据大小
        int dataSize = wavData.length - dataOffset;

        // 提取PCM数据
        byte[] pcmData = new byte[dataSize];
        System.arraycopy(wavData, dataOffset, pcmData, 0, dataSize);

        return pcmData;
    }

    /**
     * 从文件读取PCM数据，自动处理WAV和MP3格式
     *
     * @param filePath 音频文件路径
     * @return PCM数据字节数组
     */
    public static byte[] readAsPcm(String filePath) throws IOException {
        if (filePath.toLowerCase().endsWith(".wav")) {
            return wavToPcm(filePath);
        } else if (filePath.toLowerCase().endsWith(".mp3")) {
            return mp3ToPcm(filePath);
        } else if (filePath.toLowerCase().endsWith(".pcm")) {
            // 直接读取PCM文件
            return Files.readAllBytes(Paths.get(filePath));
        } else {
            throw new IOException("不支持的音频格式: " + filePath);
        }
    }

    /**
     * 将MP3转换为PCM格式
     *
     * @param mp3Path MP3文件路径
     * @return PCM数据字节数组
     */
    public static byte[] mp3ToPcm(String mp3Path) throws IOException {
        try {
            // 创建临时PCM文件
            String tempPcmPath = AUDIO_PATH + UUID.randomUUID().toString().replace("-", "") + ".pcm";

            // 构建ffmpeg命令：将MP3转换为16kHz, 单声道, 16位PCM
            String[] command = {
                    "ffmpeg",
                    "-i", mp3Path,
                    "-ar", String.valueOf(SAMPLE_RATE),
                    "-ac", String.valueOf(CHANNELS),
                    "-f", "s16le", // 16位有符号小端序PCM
                    tempPcmPath
            };

            // 执行命令
            Process process = Runtime.getRuntime().exec(command);

            // 读取错误输出以便调试
            try (InputStream errorStream = process.getErrorStream()) {
                byte[] buffer = new byte[1024];
                while (errorStream.read(buffer) != -1) {
                    // 可以选择记录错误输出
                    // logger.debug(new String(buffer));
                }
            }

            // 等待进程完成
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("ffmpeg转换失败，退出代码: " + exitCode);
            }

            // 读取生成的PCM文件
            byte[] pcmData = Files.readAllBytes(Paths.get(tempPcmPath));

            // 删除临时文件
            Files.delete(Paths.get(tempPcmPath));

            return pcmData;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffmpeg处理被中断", e);
        } catch (Exception e) {
            logger.error("使用ffmpeg转换MP3失败", e);
            throw new IOException("使用ffmpeg转换MP3失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检测音频文件格式并返回MIME类型
     *
     * @param filePath 音频文件路径
     * @return MIME类型字符串
     */
    public static String getMimeType(String filePath) {
        if (filePath.toLowerCase().endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (filePath.toLowerCase().endsWith(".wav")) {
            return "audio/wav";
        } else if (filePath.toLowerCase().endsWith(".pcm")) {
            return "audio/x-pcm";
        } else if (filePath.toLowerCase().endsWith(".opus")) {
            return "audio/opus";
        } else {
            return "application/octet-stream";
        }
    }

    /**
     * 获取音频文件的时长
     * 使用ffprobe
     * 
     * @param path 音频文件路径
     * @return 时长（秒），失败返回-1
     */
    public static double getAudioDuration(Path path) {
        String pathStr = path.toString();
        
        try {

            // 使用ffprobe获取时长
            String[] command = {
                    "ffprobe",
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    path.toFile().getAbsolutePath()
            };
            Process process = Runtime.getRuntime().exec(command);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    return Double.parseDouble(line.trim());
                }
            }
            process.waitFor();
        } catch (Exception e) {
            logger.debug("获取音频时长失败: {}", pathStr, e);
        }
        return -1;
    }
}
package com.xiaozhi.utils;

import org.gagravarr.ogg.*;
import org.gagravarr.opus.*;
import org.slf4j.Logger;

import javazoom.jl.decoder.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class AudioUtils {
    /** 由 {@link com.xiaozhi.common.config.RuntimePathConfig} 在启动时初始化 */
    public static String AUDIO_PATH;

    public static final int AUDIO_RETENTION_DAYS = 30;
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(AudioUtils.class);
    public static final int FRAME_SIZE = 960;
    public static final int SAMPLE_RATE = 16000; // 采样率
    public static final int CHANNELS = 1; // 单声道
    public static final int BITRATE = 48000; // 48kbps比特率（高质量，接近透明质量）
    public static final int SAMPLE_FORMAT = 1; // AV_SAMPLE_FMT_S16, 16位PCM
    public static final int BUFFER_SIZE = 512; // 窗口大小
    public static final int OPUS_FRAME_DURATION_MS = 60; // OPUS帧持续时间（毫秒）


    /**
     * 删除文件（静默处理异常）
     */
    public static void deleteFile(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException e) {
            logger.warn("删除文件失败: {}", path, e);
        }
    }

    public static void deleteDirectory(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    logger.warn("删除失败: {}", path, e);
                }
            });
        } catch (IOException e) {
            logger.warn("删除目录失败: {}", dir, e);
        }
    }

    public static String saveAsWav(byte[] audio) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileName = uuid + ".wav";
        Path path = Path.of(AUDIO_PATH , fileName);
        saveAsWav(path, audio);
        return AUDIO_PATH + fileName;
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
        } catch (IOException e) {
            logger.error("写入WAV文件时发生错误", e);
        }
    }

    /**
     * 合并多个音频文件为一个WAV文件
     * 支持合并的格式： wav, mp3, pcm
     *
     * @param path 输出的WAV文件路径
     * @param audioPaths 要合并的音频文件路径列表
     */
    public static void mergeAudioFiles(Path path, List<String> audioPaths) {
        if (audioPaths.size() == 1) {
            // 单文件直接移动，避免不必要的读取和重新编码
            try {
                var sourcePath = Paths.get(audioPaths.getFirst());
                if (!sourcePath.isAbsolute()) {
                    sourcePath = Paths.get(AUDIO_PATH, audioPaths.getFirst());
                }
                Files.createDirectories(path.getParent());
                Files.move(sourcePath, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (Exception e) {
                logger.warn("文件移动失败，回退到合并逻辑: {}", e.getMessage());
            }
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
                byte[] pcmData;

                pcmData = readAsPcm(fullPath);
                
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
            // 因为会采用音频缓存，所以不需要删除已经合并了的文件。
            // for (var audioPath : audioPaths) {
            //     var fullPath = audioPath.startsWith(AUDIO_PATH) ? audioPath : AUDIO_PATH + audioPath;
            //     Files.deleteIfExists(Paths.get(fullPath));
            // }

        } catch (Exception e) {
            logger.error("合并音频文件时发生错误", e);
        }
    }

    /**
     * 从WAV字节数组中提取PCM数据
     *
     * @param wavData WAV文件的原始字节数组
     * @return PCM数据字节数组
     */
    public static byte[] wavToPcm(byte[] wavData) throws IOException {
        if (wavData == null || wavData.length < 44) {
            throw new IOException("无效的WAV数据");
        }

        if (wavData[0] != 'R' || wavData[1] != 'I' || wavData[2] != 'F' || wavData[3] != 'F' ||
                wavData[8] != 'W' || wavData[9] != 'A' || wavData[10] != 'V' || wavData[11] != 'E') {
            throw new IOException("不是有效的WAV文件格式");
        }

        int dataOffset = -1;
        for (int i = 12; i < wavData.length - 4; i++) {
            if (wavData[i] == 'd' && wavData[i + 1] == 'a' && wavData[i + 2] == 't' && wavData[i + 3] == 'a') {
                dataOffset = i + 8;
                break;
            }
        }

        if (dataOffset == -1) {
            throw new IOException("在WAV文件中找不到data子块");
        }

        int dataSize = wavData.length - dataOffset;
        byte[] pcmData = new byte[dataSize];
        System.arraycopy(wavData, dataOffset, pcmData, 0, dataSize);
        return pcmData;
    }

    /**
     * 从WAV文件中提取PCM数据
     *
     * @param wavPath WAV文件路径
     * @return PCM数据字节数组
     */
    public static byte[] wavToPcm(String wavPath) throws IOException {

        byte[] wavData = Files.readAllBytes(Paths.get(wavPath));

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
     * 判断文件是否为 OGG Opus 格式（.ogg 或 .opus 扩展名）
     */
    public static boolean isOggOpus(String filePath) {
        String lower = filePath.toLowerCase();
        return lower.endsWith(".ogg") || lower.endsWith(".opus");
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
        } else if (isOggOpus(filePath)) {
            return opusToPcm(filePath);
        } else {
            throw new IOException("不支持的音频格式: " + filePath);
        }
    }

    /**
     * 从文件读取PCM数据并按Opus帧大小（3840字节 = 60ms）分块返回。
     * 避免将整个音频文件作为单个byte[]持有，减少内存峰值。
     *
     * @param filePath 音频文件路径
     * @return PCM数据分块列表，每块3840字节（最后一块可能更小）
     */
    public static List<byte[]> readAsPcmChunks(String filePath) throws IOException {
        byte[] pcmData = readAsPcm(filePath);
        // 每个Opus帧对应的PCM大小：60ms × 16000Hz × 16bit / 8 = 3840 bytes
        int chunkSize = OPUS_FRAME_DURATION_MS * SAMPLE_RATE * 2 / 1000; // 3840
        List<byte[]> chunks = new ArrayList<>();
        for (int i = 0; i < pcmData.length; i += chunkSize) {
            int end = Math.min(i + chunkSize, pcmData.length);
            byte[] chunk = new byte[end - i];
            System.arraycopy(pcmData, i, chunk, 0, end - i);
            chunks.add(chunk);
        }
        return chunks;
    }

    /**
     * 从文件读取Opus帧数据，自动处理各种音频格式
     *
     * @param filePath 音频文件路径
     * @return Opus帧列表
     */
    public static List<byte[]> readAsOpus(String filePath) throws IOException {
        if (isOggOpus(filePath)) {
            // 直接读取 OGG Opus 文件
            return readOpus(new File(filePath));
        } else {
            // 其他格式先转为 PCM，再编码为 Opus
            byte[] pcmData = readAsPcm(filePath);
            return new OpusProcessor().pcmToOpus(pcmData, false);
        }
    }

    /**
     * 将PCM数据从指定采样率重采样到目标采样率（线性插值）
     * 适用于实时流式场景（纯内存操作，无I/O延迟）
     *
     * @param pcmData      原始PCM数据（16位有符号小端序）
     * @param fromRate     源采样率（Hz），如 24000
     * @param toRate       目标采样率（Hz），如 16000
     * @return 重采样后的PCM数据
     */
    public static byte[] resamplePcm(byte[] pcmData, int fromRate, int toRate) {
        if (fromRate == toRate || pcmData == null || pcmData.length == 0) {
            return pcmData;
        }

        // 每个样本 2 字节（16位）
        int inputSamples = pcmData.length / 2;
        int outputSamples = (int) Math.ceil((long) inputSamples * toRate / fromRate);
        byte[] output = new byte[outputSamples * 2];

        for (int i = 0; i < outputSamples; i++) {
            // 源采样位置（浮点）
            double srcPos = (double) i * fromRate / toRate;
            int srcIndex = (int) srcPos;
            double frac = srcPos - srcIndex;

            // 读取相邻两个样本（16位小端序有符号）
            short s0 = readShortLE(pcmData, srcIndex);
            short s1 = (srcIndex + 1 < inputSamples) ? readShortLE(pcmData, srcIndex + 1) : s0;

            // 线性插值
            short interpolated = (short) Math.round(s0 + frac * (s1 - s0));

            // 写入输出（小端序）
            output[i * 2] = (byte) (interpolated & 0xFF);
            output[i * 2 + 1] = (byte) ((interpolated >> 8) & 0xFF);
        }

        return output;
    }

    /**
     * 将 float[] PCM 样本（范围 -1.0 ~ 1.0）转换为 16-bit PCM byte[]（小端序）
     */
    public static byte[] floatToPcm16(float[] samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float sample : samples) {
            float clamped = Math.max(-1.0f, Math.min(1.0f, sample));
            buffer.putShort((short) (clamped * 32767));
        }
        return buffer.array();
    }

    private static short readShortLE(byte[] data, int index) {
        int byteIndex = index * 2;
        if (byteIndex + 1 >= data.length) return 0;
        return (short) ((data[byteIndex] & 0xFF) | (data[byteIndex + 1] << 8));
    }

    /**
     * 将MP3转换为PCM格式
     *
     * @param mp3Path MP3文件路径
     * @return PCM数据字节数组（16kHz 16bit mono）
     */
    public static byte[] mp3ToPcm(String mp3Path) throws IOException {
        try (FileInputStream fis = new FileInputStream(mp3Path)) {
            Bitstream bitstream = new Bitstream(fis);
            Decoder decoder = new Decoder();
            ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
            int mp3SampleRate = -1;

            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer output =
                        (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (mp3SampleRate < 0) {
                    mp3SampleRate = output.getSampleFrequency();
                }
                short[] samples = output.getBuffer();
                int len = output.getBufferLength();
                byte[] frameBytes = new byte[len * 2];
                for (int i = 0; i < len; i++) {
                    frameBytes[i * 2] = (byte) (samples[i] & 0xFF);
                    frameBytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
                }
                pcmOut.write(frameBytes);
                bitstream.closeFrame();
            }
            bitstream.close();

            byte[] pcmData = pcmOut.toByteArray();
            // 如果 MP3 采样率不是 16kHz，进行重采样
            if (mp3SampleRate > 0 && mp3SampleRate != SAMPLE_RATE) {
                pcmData = resamplePcm(pcmData, mp3SampleRate, SAMPLE_RATE);
            }
            return pcmData;
        } catch (BitstreamException | DecoderException e) {
            throw new IOException("JLayer 解码 MP3 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 合并多个 PCM 帧为一个连续的字节数组
     */
    public static byte[] joinPcmFrames(List<byte[]> pcmFrames) {
        if (pcmFrames == null || pcmFrames.isEmpty()) {
            return new byte[0];
        }
        int totalSize = pcmFrames.stream().mapToInt(frame -> frame.length).sum();
        byte[] fullPcmData = new byte[totalSize];
        int offset = 0;
        for (byte[] frame : pcmFrames) {
            System.arraycopy(frame, 0, fullPcmData, offset, frame.length);
            offset += frame.length;
        }
        return fullPcmData;
    }

    /**
     * 读取标准Ogg Opus文件并转换为PCM数据
     *
     * @param opusFilePath Ogg Opus文件路径
     * @return PCM数据
     * @throws IOException 文件读取异常
     */
    public static byte[] opusToPcm(String opusFilePath) throws IOException {
        // 读取 Opus 帧
        List<byte[]> opusFrames = readOpus(new File(opusFilePath));

        if (opusFrames.isEmpty()) {
            throw new IOException("Opus文件为空或读取失败");
        }

        OpusProcessor opusProcessor = new OpusProcessor();

        // 解码所有帧为 PCM
        List<byte[]> pcmChunks = new ArrayList<>();
        for (byte[] opusFrame : opusFrames) {
            try {
                byte[] pcmData = opusProcessor.opusToPcm(opusFrame);
                if (pcmData != null && pcmData.length > 0) {
                    pcmChunks.add(pcmData);
                }
            } catch (Exception e) {
                // 静默跳过损坏的帧
            }
        }

        if (pcmChunks.isEmpty()) {
            throw new IOException("没有有效的PCM数据");
        }

        // 计算总大小并合并所有 PCM 数据
        int totalSize = pcmChunks.stream().mapToInt(chunk -> chunk.length).sum();
        byte[] result = new byte[totalSize];

        int offset = 0;
        for (byte[] chunk : pcmChunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }

        return result;
    }

    /**
     * 保存Opus帧数据为标准Ogg Opus文件
     *
     * @param opusFrames Opus帧数据列表
     * @param filePath 保存文件路径
     * @throws IOException 文件操作异常
     */
    public static void saveAsOpus(List<byte[]> opusFrames, String filePath) throws IOException {
        if (opusFrames == null || opusFrames.isEmpty()) {
            return;
        }

        // 创建OpusInfo对象，设置基本参数
        OpusInfo oi = new OpusInfo();
        oi.setSampleRate(SAMPLE_RATE);
        oi.setNumChannels(CHANNELS);
        oi.setPreSkip(0);

        // 创建OpusTags对象
        OpusTags ot = new OpusTags();
        ot.addComment("TITLE", "Xiaozhi TTS Audio");
        ot.addComment("ARTIST", "Xiaozhi ESP32 Server");

        // 使用try-with-resources管理所有资源
        try (FileOutputStream fos = new FileOutputStream(filePath);
             OpusFile opusFile = new OpusFile(fos, oi, ot)) {

            // 写入每个Opus帧
            for (byte[] frame : opusFrames) {
                opusFile.writeAudioData(new OpusAudioData(frame));
            }
        }
    }

    /**
     * 获取音频文件的时长
     *
     * @param path 音频文件路径
     * @return 时长（秒），失败返回-1
     */
    public static double getAudioDuration(Path path) {
        String pathStr = path.toString().toLowerCase();
        try {
            if (pathStr.endsWith(".wav")) {
                return getWavDuration(path);
            } else if (isOggOpus(pathStr)) {
                return getOpusDuration(path);
            } else if (pathStr.endsWith(".mp3")) {
                return getMp3Duration(path);
            } else if (pathStr.endsWith(".pcm")) {
                long fileSize = Files.size(path);
                return (double) fileSize / (SAMPLE_RATE * CHANNELS * 2);
            }
        } catch (Exception e) {
            logger.debug("获取音频时长失败: {}", path, e);
        }
        return -1;
    }

    private static double getWavDuration(Path path) throws IOException {
        byte[] header = new byte[44];
        try (InputStream is = Files.newInputStream(path)) {
            if (is.read(header) < 44) return -1;
        }
        // 读取采样率（字节 24-27，小端序）
        int sampleRate = (header[24] & 0xFF) | ((header[25] & 0xFF) << 8)
                | ((header[26] & 0xFF) << 16) | ((header[27] & 0xFF) << 24);
        // 读取字节率（字节 28-31，小端序）
        int byteRate = (header[28] & 0xFF) | ((header[29] & 0xFF) << 8)
                | ((header[30] & 0xFF) << 16) | ((header[31] & 0xFF) << 24);
        if (byteRate <= 0) return -1;
        long dataSize = Files.size(path) - 44;
        return (double) dataSize / byteRate;
    }

    private static double getOpusDuration(Path path) throws IOException {
        List<byte[]> frames = readOpus(path.toFile());
        if (frames.isEmpty()) return -1;
        // 每帧 60ms（OPUS_FRAME_DURATION_MS）
        return frames.size() * OPUS_FRAME_DURATION_MS / 1000.0;
    }

    private static double getMp3Duration(Path path) throws IOException {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            Bitstream bitstream = new Bitstream(fis);
            double totalSeconds = 0;
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                totalSeconds += header.ms_per_frame() / 1000.0;
                bitstream.closeFrame();
            }
            bitstream.close();
            return totalSeconds;
        } catch (BitstreamException e) {
            throw new IOException("JLayer 读取 MP3 时长失败", e);
        }
    }

    /**
     * 读取标准Ogg Opus文件
     *
     * @param file Ogg Opus文件
     * @return Opus帧列表
     * @throws IOException 文件读取异常
     */
    public static List<byte[]> readOpus(File file) {
        List<byte[]> frames = new ArrayList<>();

        if (file.length() <= 0) {
            return frames;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            OggFile oggFile = new OggFile(fis);
            try (OpusFile opusFile = new OpusFile(oggFile)) {
                OpusAudioData audioData;
                while ((audioData = opusFile.getNextAudioPacket()) != null) {
                    byte[] frameData = audioData.getData();
                    if (frameData != null && frameData.length > 0) {
                        frames.add(frameData);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("读取Ogg Opus文件失败: {}", file.getAbsolutePath(), e);
            return frames;
        }

        return frames;
    }

    /**
     * 从输入流读取 Ogg Opus 格式的音频帧（用于从云存储字节数组解析）。
     *
     * @param inputStream Ogg Opus 数据流
     * @return Opus帧列表
     */
    public static List<byte[]> readOpus(InputStream inputStream) {
        List<byte[]> frames = new ArrayList<>();
        try {
            OggFile oggFile = new OggFile(inputStream);
            try (OpusFile opusFile = new OpusFile(oggFile)) {
                OpusAudioData audioData;
                while ((audioData = opusFile.getNextAudioPacket()) != null) {
                    byte[] frameData = audioData.getData();
                    if (frameData != null && frameData.length > 0) {
                        frames.add(frameData);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("从输入流读取Ogg Opus失败", e);
        }
        return frames;
    }
}
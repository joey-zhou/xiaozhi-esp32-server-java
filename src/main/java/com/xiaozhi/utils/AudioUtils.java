package com.xiaozhi.utils;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;
import org.slf4j.Logger;

public class AudioUtils {
    public static final String AUDIO_PATH = "audio/";
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(AudioUtils.class);
    public static final int SAMPLE_RATE = 16000; // 采样率
    public static final int CHANNELS = 1; // 单声道
    public static final int BITRATE = 128000; // 128kbps比特率
    public static final int SAMPLE_FORMAT = avutil.AV_SAMPLE_FMT_S16; // 16位PCM

    /**
     * 将原始音频数据保存为MP3文件
     * 
     * @param audio 音频数据
     * @return 文件名
     *
     */
    public static String saveAsMp3File(byte[] audio) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileName = uuid + ".mp3";
        String filePath = AUDIO_PATH + fileName;

        try {
            // 创建内存中的帧记录器，直接输出到MP3文件
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(filePath, CHANNELS);
            recorder.setAudioChannels(CHANNELS);
            recorder.setSampleRate(SAMPLE_RATE);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_MP3);
            recorder.setAudioQuality(0); // 高质量
            recorder.setAudioBitrate(BITRATE); // 128kbps比特率
            recorder.setSampleFormat(avutil.AV_SAMPLE_FMT_S16); // 16位PCM
            recorder.start();

            // 创建音频帧
            Frame audioFrame = new Frame();

            // 将PCM数据转换为短整型数组
            short[] samples = new short[audio.length / 2];
            ByteArrayInputStream bais = new ByteArrayInputStream(audio);
            for (int i = 0; i < samples.length; i++) {
                // 读取两个字节，组合成一个short (小端序)
                int low = bais.read() & 0xff;
                int high = bais.read() & 0xff;
                samples[i] = (short) (low | (high << 8));
            }

            // 创建ShortBuffer并填充数据
            java.nio.ShortBuffer shortBuffer = java.nio.ShortBuffer.wrap(samples);

            // 设置音频帧数据
            audioFrame.samples = new java.nio.Buffer[] { shortBuffer };
            audioFrame.sampleRate = SAMPLE_RATE;
            audioFrame.audioChannels = CHANNELS;

            // 记录音频帧
            recorder.record(audioFrame);

            // 关闭记录器
            recorder.stop();
            recorder.release();
            recorder.close();
            return fileName;
        } catch (FrameRecorder.Exception e) {
            logger.error("编码MP3时发生错误", e);
        }
        return null;
    }

    /**
     * 将原始音频数据保存为WAV文件
     * 
     * @param audio 音频数据
     * @return 文件名
     *
     */
    public static String saveAsWavFile(byte[] audioData) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileName = uuid + ".mp3";
        String filePath = AUDIO_PATH + fileName;
        // WAV文件参数
        int sampleRate = 16000; // 与Recognizer使用的采样率保持一致
        int channels = 1; // 单声道
        int bitsPerSample = 16; // 16位采样

        try (FileOutputStream fos = new FileOutputStream(filePath);
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
            dos.writeShort(Short.reverseBytes((short) channels)); // 通道数
            dos.writeInt(Integer.reverseBytes(sampleRate)); // 采样率
            dos.writeInt(Integer.reverseBytes(sampleRate * channels * bitsPerSample / 8)); // 字节率
            dos.writeShort(Short.reverseBytes((short) (channels * bitsPerSample / 8))); // 块对齐
            dos.writeShort(Short.reverseBytes((short) bitsPerSample)); // 每个样本的位数

            // data子块
            dos.writeBytes("data");
            dos.writeInt(Integer.reverseBytes(audioData.length)); // 数据大小

            // 写入音频数据
            dos.write(audioData);
            return fileName;
        } catch (FrameRecorder.Exception e) {
            logger.error("编码MP3时发生错误", e);
        } catch (IOException e) {
            logger.error("写入WAV文件时发生错误", e);
        }
        return null;
    }

}

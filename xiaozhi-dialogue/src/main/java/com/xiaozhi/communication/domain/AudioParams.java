package com.xiaozhi.communication.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AudioParams {
    /** 服务端音频处理链路（Opus编解码、VAD、AEC、STT）固定的格式 */
    public static final String SERVER_FORMAT = "opus";
    public static final int SERVER_SAMPLE_RATE = 16000;
    public static final int SERVER_CHANNELS = 1;
    public static final int SERVER_FRAME_DURATION = 60;

    private int channels;
    private String format;
    private int sampleRate;
    private int frameDuration;

    /** 每次返回新实例，避免下发对象被调用方改动而污染其他会话 */
    public static AudioParams serverCapability() {
        return new AudioParams()
                .setChannels(SERVER_CHANNELS)
                .setFormat(SERVER_FORMAT)
                .setSampleRate(SERVER_SAMPLE_RATE)
                .setFrameDuration(SERVER_FRAME_DURATION);
    }

    /**
     * 与服务端处理能力比对，返回不一致项描述；一致或未声明时返回 null。
     * 字段为 0/null 表示设备未声明该项，不参与比对。
     */
    public String mismatchAgainstServer() {
        List<String> diffs = new ArrayList<>();
        if (format != null && !SERVER_FORMAT.equalsIgnoreCase(format)) {
            diffs.add("格式=" + format + "(服务端仅支持" + SERVER_FORMAT + ")");
        }
        if (sampleRate > 0 && sampleRate != SERVER_SAMPLE_RATE) {
            diffs.add("采样率=" + sampleRate + "(服务端按" + SERVER_SAMPLE_RATE + "处理)");
        }
        if (channels > 0 && channels != SERVER_CHANNELS) {
            diffs.add("声道=" + channels + "(服务端按" + SERVER_CHANNELS + "处理)");
        }
        if (frameDuration > 0 && frameDuration != SERVER_FRAME_DURATION) {
            diffs.add("帧时长=" + frameDuration + "ms(服务端下发" + SERVER_FRAME_DURATION + "ms)");
        }
        return diffs.isEmpty() ? null : String.join("，", diffs);
    }
}

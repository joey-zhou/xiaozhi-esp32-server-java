package com.xiaozhi.common;

import lombok.Getter;

/**
 * 音频播放对象，主要是封装语音字节数组（必须），并添加文本信息（非必须）。
 * 数据格式可以是 PCM（默认）或预编码的 Opus 帧（opusEncoded=true）。
 */
public class Speech extends org.springframework.ai.audio.tts.Speech {

    @Getter
    private String text="";

    /**
     * 标记数据是否已经是 Opus 编码帧。
     * true: getOutput() 返回的是单个 Opus 帧，Player 无需再做 PCM→Opus 转换。
     * false（默认）: getOutput() 返回的是 PCM 数据，需要经过 Opus 编码。
     */
    @Getter
    private boolean opusEncoded = false;

    public Speech(byte[] speech) {
        super(speech);
    }
    public Speech(byte[] speech, String text) {
        super(speech);
        this.text = text;
    }

    /**
     * 设置文本信息（用于缓存命中场景，在第一帧上附加文本）
     */
    public Speech withText(String text) {
        this.text = text;
        return this;
    }

    /**
     * 创建预编码 Opus 帧的 Speech 对象
     */
    public static Speech ofOpus(byte[] opusFrame) {
        Speech s = new Speech(opusFrame);
        s.opusEncoded = true;
        return s;
    }

    /**
     * 创建预编码 Opus 帧的 Speech 对象（带文本）
     */
    public static Speech ofOpus(byte[] opusFrame, String text) {
        Speech s = new Speech(opusFrame, text);
        s.opusEncoded = true;
        return s;
    }
}

package com.xiaozhi.dialogue.service;

import lombok.Getter;

/**
 * 音频播放对象，主要是封装语音字节数组（必须），并添加文本信息（非必须）
 */
public class Speech extends org.springframework.ai.audio.tts.Speech {

    @Getter
    private String text="";

    public Speech(byte[] speech) {
        super(speech);
    }
    public Speech(byte[] speech, String text) {
        super(speech);
        this.text = text;
    }
}

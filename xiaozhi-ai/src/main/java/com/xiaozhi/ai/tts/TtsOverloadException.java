package com.xiaozhi.ai.tts;

/**
 * 本地合成因并发准入被拒：队列已满，或排队时间超过上限。
 * 与合成失败区分开，调用方据此判定这句是被主动放弃的而不是模型出错。
 */
public class TtsOverloadException extends Exception {

    public TtsOverloadException(String message) {
        super(message);
    }
}

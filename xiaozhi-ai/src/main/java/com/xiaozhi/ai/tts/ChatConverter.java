package com.xiaozhi.ai.tts;

import reactor.core.publisher.Flux;

@FunctionalInterface
public interface ChatConverter {

    /**
     * 将 tokens 字符串转换为SentenceResult（纯文本 + 情绪词）。
     * @return
     */
    Flux<SentenceHelper.SentenceResult> convert(Flux<String> stringFlux);
}

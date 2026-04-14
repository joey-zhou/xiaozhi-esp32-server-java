package com.xiaozhi.ai.tts;

import reactor.core.publisher.Flux;

@FunctionalInterface
public interface ChatConverter {

    /**
     * 将 tokens 字符串转换为Sentences。
     * @return
     */
    Flux<String> convert(Flux<String> stringFlux);
}

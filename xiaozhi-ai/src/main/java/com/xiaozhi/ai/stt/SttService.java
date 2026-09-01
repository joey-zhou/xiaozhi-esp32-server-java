package com.xiaozhi.ai.stt;

import reactor.core.publisher.Flux;

import java.util.function.Consumer;

/**
 * STT服务接口
 */
public interface SttService {

  /**
   * 获取服务提供商名称
   */
  String getProviderName();

  /**
   * 流式处理音频数据
   *
   * @param audioSink 音频数据流
   * @return 识别结果，包含文本及可选的情感信息
   */
  SttResult stream(Flux<byte[]> audioSink);

  /**
   * 流式处理音频数据，识别出中间结果时回调。未覆写的实现拿不到中间结果。
   *
   * @param onPartialText 中间结果回调，会被多次调用，文本随识别推进而变化
   */
  default SttResult stream(Flux<byte[]> audioSink, Consumer<String> onPartialText) {
    return stream(audioSink);
  }

}

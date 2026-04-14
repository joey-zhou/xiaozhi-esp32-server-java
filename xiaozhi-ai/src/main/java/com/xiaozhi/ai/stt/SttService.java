package com.xiaozhi.ai.stt;

import reactor.core.publisher.Flux;

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

}

package com.xiaozhi.dialogue.stt;

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
   * 处理音频数据（非流式）
   * 
   * @param audioData 音频字节数组
   * @return 识别的文本结果
   */
  String recognition(byte[] audioData);

  /**
   * 流式处理音频数据
   * 
   * @param audioSink 音频数据流
   * @return 识别的文本结果流
   */
  String streamRecognition(Flux<byte[]> audioSink);

  /**
   * 检查服务是否支持流式处理
   * 
   * @return 是否支持流式处理
   */
  default boolean supportsStreaming() {
    return false;
  }
}

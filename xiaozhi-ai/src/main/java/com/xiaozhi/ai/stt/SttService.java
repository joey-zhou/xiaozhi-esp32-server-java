package com.xiaozhi.ai.stt;

import com.xiaozhi.common.annotation.MonitoredOperation;
import reactor.core.publisher.Flux;

import java.util.List;
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

  /**
   * 带热词的流式处理。只有支持热词的 provider 覆写本方法，其余走没有热词的重载，热词被忽略。
   *
   * @param hotwords 本轮生效的热词，按角色配置，可能为空
   */
  @MonitoredOperation(name = "xiaozhi.stt.stream")
  default SttResult stream(Flux<byte[]> audioSink, Consumer<String> onPartialText, List<Hotword> hotwords) {
    return stream(audioSink, onPartialText);
  }

}

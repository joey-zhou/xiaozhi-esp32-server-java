package com.xiaozhi.websocket.tts;

import java.util.function.Consumer;

/**
 * TTS服务接口
 */
public interface TtsService {

  /**
   * 获取服务提供商名称
   */
  String getProviderName();

  /**
   * 生成文件名称
   * 
   * @return 文件名称
   */
  String getAudioFileName();

  /**
   * 将文本转换为语音（带自定义语音）
   * 
   * @param text 要转换为语音的文本
   * @return 生成的音频文件路径
   */
  String textToSpeech(String text) throws Exception;

  /**
   * 流式将文本转换为语音
   * 
   * @param text              要转换为语音的文本
   * @param audioDataConsumer 音频数据消费者，接收PCM格式的音频数据块
   * @throws Exception 转换过程中可能发生的异常
   */
  void streamTextToSpeech(String text, Consumer<byte[]> audioDataConsumer) throws Exception;

}

package com.xiaozhi.dialogue.tts;

import java.util.UUID;

/**
 * TTS服务接口
 * TODO 逐步重构为 spring ai 的TextToSpeechModel,Getter的参数信息+ TtsConfig逐步重构为TextToSpeechOptions
 */
public interface TtsService {

  /**
   * 获取服务提供商名称
   */
  String getProviderName();

  /**
   * 获取音色名称
   */
  String getVoiceName();

  /**
   * 获取语速
   */
  Float getSpeed();

  /**
   * 获取音调
   */
  Float getPitch();

  /**
   * 音频格式
   */
  default String audioFormat() {
    return "wav";
  }

  /**
   * 生成文件名称
   * 
   * @return 文件名称
   */
  default String getAudioFileName() {
    return UUID.randomUUID().toString().replace("-", "") + "." + audioFormat();
  }


  /**
   * 将文本转换为语音（带自定义语音）
   * TODO return 的应该是Path而不是String
   *
   * @param text 要转换为语音的文本
   * @return 生成的音频文件路径
   */
  String textToSpeech(String text) throws Exception;


}

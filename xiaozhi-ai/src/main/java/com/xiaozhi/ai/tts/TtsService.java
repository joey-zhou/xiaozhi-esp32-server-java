package com.xiaozhi.ai.tts;

import java.nio.file.Path;
import java.util.UUID;

/**
 * TTS服务接口。
 * 重构方向：对齐 Spring AI 的 {@link org.springframework.ai.audio.tts.TextToSpeechModel}。
 * 当前接口结构与 Spring AI 的映射关系：
 * <ul>
 *   <li>TtsService → TextToSpeechModel（call 模式）</li>
 *   <li>voiceName/speed/pitch 等 Getter → TextToSpeechOptions（配置参数对象化）</li>
 * </ul>
 */
public interface TtsService {

  /**
   * 获取服务提供商名称
   */
  String getProviderName();

  /**
   * 获取 TTS 参数配置
   */
  XiaozhiTtsOptions getOptions();

  /**
   * 获取音色名称
   */
  default String getVoiceName() {
    return getOptions().getVoiceName();
  }

  /**
   * 获取语速
   */
  default Float getSpeed() {
    return getOptions().getSpeed();
  }

  /**
   * 获取音调
   */
  default Float getPitch() {
    return getOptions().getPitch();
  }

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
   *
   * @param text 要转换为语音的文本
   * @return 生成的音频文件路径
   */
  Path textToSpeech(String text) throws Exception;


}

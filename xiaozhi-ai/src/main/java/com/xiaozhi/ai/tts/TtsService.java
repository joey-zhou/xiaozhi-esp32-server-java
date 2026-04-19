package com.xiaozhi.ai.tts;

import java.nio.file.Path;
import java.util.UUID;

/**
 * TTS服务接口。
 * 与 Spring AI 的 {@link org.springframework.ai.audio.tts.TextToSpeechModel} 对齐关系：
 * <ul>
 *   <li>TtsService → TextToSpeechModel（call 模式） — 通过 {@link TtsServiceAdapter} 桥接</li>
 *   <li>{@link XiaozhiTtsOptions} → TextToSpeechOptions — 直接实现</li>
 * </ul>
 *
 * @see TtsServiceAdapter 将 TtsService 适配为 Spring AI TextToSpeechModel 的适配器
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
  default Double getSpeed() {
    return getOptions().getSpeed();
  }

  /**
   * 获取音调
   */
  default Double getPitch() {
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

import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { testVoice } from '@/services/role'
import { getResourceUrl } from '@/utils/resource'

/**
 * 音频播放 Composable
 * 统一封装音频播放逻辑，支持两种模式：
 * 1. 直接播放音频路径
 * 2. 通过API获取音频再播放
 */
export function useAudioPlayer() {
  const { t } = useI18n()

  // 播放状态
  const playingAudioId = ref<string>('')
  const loadingAudioId = ref<string>('') // loading状态（API请求期间）
  const audioCache = new Map<string, HTMLAudioElement>()

  /**
   * 直接播放音频文件（用于剧本脚本播放）
   * @param audioPath 音频路径
   * @param audioId 音频唯一标识
   */
  const playAudioDirect = async (audioPath: string, audioId: string): Promise<boolean> => {
    try {
      // 如果正在播放同一个音频，则停止
      if (playingAudioId.value === audioId) {
        const audio = audioCache.get(audioId)
        if (audio) {
          audio.pause()
          audio.currentTime = 0
        }
        playingAudioId.value = ''
        return true
      }

      // 停止其他正在播放的音频
      stopAllAudio()

      // 创建或获取音频元素
      let audio = audioCache.get(audioId)
      if (!audio) {
        audio = new Audio()
        audioCache.set(audioId, audio)

        // 设置音频结束回调
        audio.onended = () => {
          if (playingAudioId.value === audioId) {
            playingAudioId.value = ''
          }
        }

        // 设置错误回调
        audio.onerror = () => {
          message.error(t('common.audioPlayFailed'))
          if (playingAudioId.value === audioId) {
            playingAudioId.value = ''
          }
        }
      }

      // 设置音频源并播放
      const audioUrl = getResourceUrl(audioPath)
      if (!audioUrl) {
        message.error(t('common.audioPathInvalid'))
        return false
      }
      
      audio.src = audioUrl
      
      await audio.play()
      playingAudioId.value = audioId

      return true
    } catch (error) {
      // 播放失败，清除播放状态
      // 注意：不在这里显示错误提示，因为 audio.onerror 会处理错误提示
      playingAudioId.value = ''
      return false
    }
  }

  /**
   * 通过API测试音色并播放（用于音色测试）
   * @param voiceName 音色名称
   * @param ttsId TTS配置ID
   * @param provider TTS提供商
   * @param audioId 音频唯一标识
   * @param testMessage 测试文本
   */
  const playAudioFromApi = async (
    voiceName: string,
    ttsId: number,
    provider: string,
    audioId: string,
    testMessage?: string
  ): Promise<boolean> => {
    try {
      // 如果正在播放同一个音频，则停止
      if (playingAudioId.value === audioId) {
        const audio = audioCache.get(audioId)
        if (audio) {
          audio.pause()
          audio.currentTime = 0
        }
        playingAudioId.value = ''
        return true
      }

      // 停止其他正在播放的音频
      stopAllAudio()

      // 设置loading状态（API请求期间）
      loadingAudioId.value = audioId
      
      const res = await testVoice({
        voiceName,
        ttsId,
        provider,
        message: testMessage || t('role.voiceTestMessage')
      })

      // 清除loading状态
      loadingAudioId.value = ''

      if (res.code !== 200 || !res.data?.audioUrl) {
        message.error(res.message || t('common.audioGenerateFailed'))
        return false
      }

      // 创建或获取音频元素
      let audio = audioCache.get(audioId)
      if (!audio) {
        audio = new Audio()
        audioCache.set(audioId, audio)

        // 设置音频结束回调
        audio.onended = () => {
          if (playingAudioId.value === audioId) {
            playingAudioId.value = ''
          }
        }

        // 设置错误回调
        audio.onerror = () => {
          message.error(t('common.audioPlayFailed'))
          if (playingAudioId.value === audioId) {
            playingAudioId.value = ''
          }
        }
      }

      // 设置音频源并播放
      const audioUrl = getResourceUrl(res.data.audioUrl)
      if (!audioUrl) {
        message.error(t('common.audioPathInvalid'))
        return false
      }
      
      audio.src = audioUrl
      
      await audio.play()
      
      // 播放成功后设置playing状态
      playingAudioId.value = audioId

      return true
    } catch (error) {
      console.error('测试音色失败:', error)
      message.error(t('common.audioTestFailed'))
      loadingAudioId.value = ''
      if (playingAudioId.value === audioId) {
        playingAudioId.value = ''
      }
      return false
    }
  }

  /**
   * 停止所有音频播放
   */
  const stopAllAudio = () => {
    audioCache.forEach((audio) => {
      audio.pause()
      audio.currentTime = 0
    })
    playingAudioId.value = ''
  }

  /**
   * 停止指定音频播放
   */
  const stopAudio = (audioId: string) => {
    const audio = audioCache.get(audioId)
    if (audio) {
      audio.pause()
      audio.currentTime = 0
    }
    if (playingAudioId.value === audioId) {
      playingAudioId.value = ''
    }
  }

  /**
   * 检查指定音频是否正在播放
   */
  const isPlaying = (audioId: string): boolean => {
    return playingAudioId.value === audioId
  }

  /**
   * 清理音频缓存
   */
  const clearAudioCache = () => {
    stopAllAudio()
    audioCache.forEach((audio) => {
      audio.src = ''
    })
    audioCache.clear()
  }

  return {
    playingAudioId,
    loadingAudioId,
    playAudioDirect,
    playAudioFromApi,
    stopAllAudio,
    stopAudio,
    isPlaying,
    clearAudioCache
  }
}


import { ref, onBeforeUnmount } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { testVoice } from '@/services/role'
import { getResourceUrl } from '@/utils/resource'
import { useRequest } from '@/composables/useRequest'

/**
 * 通过 API 试听音色的参数
 */
export interface PlayAudioFromApiParams {
  /** 音色名称（标准音色名，或克隆音色的 cloneId） */
  voiceName: string
  /** TTS 配置 ID */
  ttsId: number
  /** TTS 提供商 */
  provider: string
  /** 播放态标识：isPlaying/loading 与调用方 UI 高亮都按它匹配 */
  audioId: string
  /** 测试文本，缺省时用统一的试听文案 */
  testMessage?: string
  ttsPitch?: number
  ttsSpeed?: number
  /**
   * 音频缓存 key，默认等于 audioId。
   * 语速/语调等参数变了就是另一段音频，需要单独区分时传入区分好的 key，
   * 否则命中缓存会放出旧参数合成的试听音频
   */
  cacheKey?: string
}

/**
 * 音频播放 Composable
 * 统一封装音频播放逻辑，支持两种模式：
 * 1. 直接播放已知地址的音频（已合成好的试听音频等）
 * 2. 通过 API 合成音色试听音频再播放，命中缓存时直接复用，不重复调用（可能计费的）合成接口
 */
export function useAudioPlayer() {
  const { t } = useI18n()
  const { executeFull: executeTestVoice } = useRequest()

  // 播放状态
  const playingAudioId = ref<string>('')
  const loadingAudioId = ref<string>('') // loading状态（API请求期间）
  const audioCache = new Map<string, HTMLAudioElement>()

  /**
   * 绑定播放结束/出错回调。出错时把缓存一并删掉，避免一段坏链接的音频卡在缓存里，
   * 下次点播放永远失败也永远不会重新去请求
   */
  const bindAudioEvents = (audio: HTMLAudioElement, audioId: string, cacheKey: string) => {
    audio.onended = () => {
      if (playingAudioId.value === audioId) {
        playingAudioId.value = ''
      }
    }

    audio.onerror = () => {
      message.error(t('common.audioPlayFailed'))
      if (playingAudioId.value === audioId) {
        playingAudioId.value = ''
      }
      audioCache.delete(cacheKey)
    }
  }

  /**
   * 直接播放已知地址的音频（已合成好的试听音频等）
   * @param audioPath 音频路径
   * @param audioId 音频唯一标识，同时用作缓存 key
   */
  const playAudioDirect = async (audioPath: string, audioId: string): Promise<boolean> => {
    try {
      // 如果正在播放同一个音频，则停止
      if (playingAudioId.value === audioId) {
        stopAllAudio()
        return true
      }

      // 停止其他正在播放的音频
      stopAllAudio()

      // 命中缓存直接复用，不重复解析地址
      let audio = audioCache.get(audioId)
      if (!audio) {
        const audioUrl = getResourceUrl(audioPath)
        if (!audioUrl) {
          message.error(t('common.audioPathInvalid'))
          return false
        }

        audio = new Audio(audioUrl)
        audioCache.set(audioId, audio)
        bindAudioEvents(audio, audioId, audioId)
      }

      await audio.play()
      playingAudioId.value = audioId

      return true
    } catch (error) {
      // audio.onerror 已经报过一次；这里捕获的是 play() 本身被拒绝的情况（如浏览器自动播放限制）
      console.error('直接播放音频失败:', error)
      playingAudioId.value = ''
      return false
    }
  }

  /**
   * 通过 API 合成音色试听音频并播放。
   * 命中缓存时直接复用已播放过的音频、不再调用合成接口，避免每次点听都对按次计费的音色重复扣费
   */
  const playAudioFromApi = async (params: PlayAudioFromApiParams): Promise<boolean> => {
    const {
      voiceName,
      ttsId,
      provider,
      audioId,
      testMessage,
      ttsPitch,
      ttsSpeed,
      cacheKey = audioId,
    } = params

    try {
      // 如果正在播放同一个音频，则停止
      if (playingAudioId.value === audioId) {
        stopAllAudio()
        return true
      }

      // 停止其他正在播放的音频
      stopAllAudio()

      // 命中缓存直接复用，跳过合成接口
      let audio = audioCache.get(cacheKey)
      if (!audio) {
        // 设置loading状态（API请求期间）
        loadingAudioId.value = audioId

        const { ok, data } = await executeTestVoice(
          () => testVoice({
            voiceName,
            ttsId,
            provider,
            message: testMessage || t('role.voiceTestMessage'),
            ttsPitch,
            ttsSpeed,
          }),
          {
            errorText: t('common.audioGenerateFailed'),
            networkErrorText: t('common.audioTestFailed'),
          },
        )

        // 清除loading状态
        loadingAudioId.value = ''

        if (!ok) {
          return false
        }

        // 业务码 200 但没给音频地址，同样按合成失败处理
        if (!data?.audioUrl) {
          message.error(t('common.audioGenerateFailed'))
          return false
        }

        const audioUrl = getResourceUrl(data.audioUrl)
        if (!audioUrl) {
          message.error(t('common.audioPathInvalid'))
          return false
        }

        audio = new Audio(audioUrl)
        audioCache.set(cacheKey, audio)
        bindAudioEvents(audio, audioId, cacheKey)
      }

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

  // 卸载时停掉并释放缓存，否则试听音频会跟着用户跑到下一个页面继续播
  onBeforeUnmount(() => {
    clearAudioCache()
  })

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

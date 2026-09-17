import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'

const roleMock = vi.hoisted(() => ({ testVoice: vi.fn() }))
vi.mock('@/services/role', () => roleMock)

import { message } from 'ant-design-vue'
import { useAudioPlayer } from '../useAudioPlayer'

/**
 * jsdom 不实现 HTMLMediaElement 的真实播放/解码，这里用一个假的 Audio 替身：
 * 既能拿到 play/pause 的调用记录，也能手动触发 onended/onerror 来验证边界处理。
 */
class FakeAudio {
  static instances: FakeAudio[] = []
  src = ''
  currentTime = 0
  onended: (() => void) | null = null
  onerror: (() => void) | null = null
  play = vi.fn().mockResolvedValue(undefined)
  pause = vi.fn()

  constructor(src?: string) {
    if (src) this.src = src
    FakeAudio.instances.push(this)
  }
}

function mountPlayer() {
  let api!: ReturnType<typeof useAudioPlayer>
  const wrapper = mount(defineComponent({
    setup() {
      api = useAudioPlayer()
      return () => null
    },
  }))
  return { api, wrapper }
}

describe('useAudioPlayer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    FakeAudio.instances = []
    vi.stubGlobal('Audio', FakeAudio as unknown as typeof Audio)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('playAudioDirect', () => {
    it('播放成功后设置 playingAudioId，再点一次同一个音频会停止', async () => {
      const { api, wrapper } = mountPlayer()

      const played = await api.playAudioDirect('/audio/a.mp3', 'msg-1')
      expect(played).toBe(true)
      expect(api.playingAudioId.value).toBe('msg-1')
      expect(FakeAudio.instances).toHaveLength(1)

      const toggledOff = await api.playAudioDirect('/audio/a.mp3', 'msg-1')
      expect(toggledOff).toBe(true)
      expect(api.playingAudioId.value).toBe('')
      expect(FakeAudio.instances[0]!.pause).toHaveBeenCalled()
      // 同一个 audioId 复用已创建的音频元素，不重复 new 一个
      expect(FakeAudio.instances).toHaveLength(1)

      wrapper.unmount()
    })

    it('地址解析失败时报错且不创建音频', async () => {
      const { api, wrapper } = mountPlayer()

      const played = await api.playAudioDirect('', 'msg-2')

      expect(played).toBe(false)
      expect(message.error).toHaveBeenCalledWith('common.audioPathInvalid')
      expect(api.playingAudioId.value).toBe('')
      expect(FakeAudio.instances).toHaveLength(0)

      wrapper.unmount()
    })
  })

  describe('playAudioFromApi 缓存（避免重复触发计费）', () => {
    it('同一 cacheKey 命中缓存时不重复调用合成接口', async () => {
      roleMock.testVoice.mockResolvedValue({ code: 200, data: { audioUrl: '/tts/a.mp3' }, message: '' })
      const { api, wrapper } = mountPlayer()

      await api.playAudioFromApi({ voiceName: 'v1', ttsId: 1, provider: 'edge', audioId: 'v1' })
      expect(roleMock.testVoice).toHaveBeenCalledTimes(1)
      expect(api.playingAudioId.value).toBe('v1')

      // 再点一次同一个音色先触发 toggle-stop
      await api.playAudioFromApi({ voiceName: 'v1', ttsId: 1, provider: 'edge', audioId: 'v1' })
      expect(api.playingAudioId.value).toBe('')

      // 第三次重新播放：命中缓存，不应再请求合成接口
      await api.playAudioFromApi({ voiceName: 'v1', ttsId: 1, provider: 'edge', audioId: 'v1' })
      expect(roleMock.testVoice).toHaveBeenCalledTimes(1)
      expect(api.playingAudioId.value).toBe('v1')
      expect(FakeAudio.instances).toHaveLength(1)

      wrapper.unmount()
    })

    it('cacheKey 不同（如语速/语调变化）各自触发一次合成', async () => {
      roleMock.testVoice.mockResolvedValue({ code: 200, data: { audioUrl: '/tts/a.mp3' }, message: '' })
      const { api, wrapper } = mountPlayer()

      await api.playAudioFromApi({ voiceName: 'v1', ttsId: 1, provider: 'edge', audioId: 'v1', cacheKey: 'v1|1.0' })
      // 同一个 audioId 再点一次，toggle-stop，不产生新请求
      await api.playAudioFromApi({ voiceName: 'v1', ttsId: 1, provider: 'edge', audioId: 'v1', cacheKey: 'v1|1.0' })
      // 换一个 cacheKey（模拟语速变化），必须重新合成
      await api.playAudioFromApi({ voiceName: 'v1', ttsId: 1, provider: 'edge', audioId: 'v1', cacheKey: 'v1|1.2' })

      expect(roleMock.testVoice).toHaveBeenCalledTimes(2)
      expect(FakeAudio.instances).toHaveLength(2)

      wrapper.unmount()
    })

    it('合成接口返回非 200 时优先展示后端消息，不创建音频', async () => {
      roleMock.testVoice.mockResolvedValue({ code: 500, message: '欠费', data: null })
      const { api, wrapper } = mountPlayer()

      const played = await api.playAudioFromApi({ voiceName: 'v1', ttsId: 1, provider: 'edge', audioId: 'v1' })

      expect(played).toBe(false)
      expect(message.error).toHaveBeenCalledWith('欠费')
      expect(api.playingAudioId.value).toBe('')
      expect(api.loadingAudioId.value).toBe('')
      expect(FakeAudio.instances).toHaveLength(0)

      wrapper.unmount()
    })

    it('合成接口异常时展示统一的失败文案', async () => {
      roleMock.testVoice.mockRejectedValue(new Error('network down'))
      const { api, wrapper } = mountPlayer()

      const played = await api.playAudioFromApi({ voiceName: 'v1', ttsId: 1, provider: 'edge', audioId: 'v1' })

      expect(played).toBe(false)
      // 传输层失败由 useRequest 用 request-error 这个 key 覆盖拦截器那条，文案不变
      expect(message.error).toHaveBeenCalledWith({
        content: 'common.audioTestFailed',
        key: 'request-error',
      })
      expect(api.loadingAudioId.value).toBe('')
      expect(api.playingAudioId.value).toBe('')

      wrapper.unmount()
    })

    it('播放出错时把缓存一并清掉，下次播放会重新请求合成接口', async () => {
      roleMock.testVoice.mockResolvedValue({ code: 200, data: { audioUrl: '/tts/a.mp3' }, message: '' })
      const { api, wrapper } = mountPlayer()

      await api.playAudioFromApi({ voiceName: 'v1', ttsId: 1, provider: 'edge', audioId: 'v1' })
      expect(roleMock.testVoice).toHaveBeenCalledTimes(1)

      // 模拟这段缓存音频加载失败
      FakeAudio.instances[0]!.onerror?.()
      expect(message.error).toHaveBeenCalledWith('common.audioPlayFailed')
      expect(api.playingAudioId.value).toBe('')

      await api.playAudioFromApi({ voiceName: 'v1', ttsId: 1, provider: 'edge', audioId: 'v1' })
      expect(roleMock.testVoice).toHaveBeenCalledTimes(2)

      wrapper.unmount()
    })
  })

  describe('卸载清理', () => {
    it('组件卸载时停止播放并释放缓存', async () => {
      roleMock.testVoice.mockResolvedValue({ code: 200, data: { audioUrl: '/tts/a.mp3' }, message: '' })
      const { api, wrapper } = mountPlayer()

      await api.playAudioFromApi({ voiceName: 'v1', ttsId: 1, provider: 'edge', audioId: 'v1' })
      expect(api.playingAudioId.value).toBe('v1')

      wrapper.unmount()

      expect(api.playingAudioId.value).toBe('')
      expect(FakeAudio.instances[0]!.pause).toHaveBeenCalled()
      expect(FakeAudio.instances[0]!.src).toBe('')
    })
  })
})

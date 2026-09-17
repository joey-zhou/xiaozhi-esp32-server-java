import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'

const waveSurferInstance = vi.hoisted(() => ({
  on: vi.fn(),
  load: vi.fn().mockResolvedValue(undefined),
  play: vi.fn(),
  pause: vi.fn(),
  playPause: vi.fn(),
  seekTo: vi.fn(),
  setOptions: vi.fn(),
  destroy: vi.fn(),
}))
const createMock = vi.hoisted(() =>
  vi.fn((_options: Record<string, unknown>) => waveSurferInstance),
)

vi.mock('wavesurfer.js', () => ({ default: { create: createMock } }))

import AudioPlayer from '../AudioPlayer.vue'

/**
 * jsdom 没有 IntersectionObserver，这里装一个可手工触发的替身。
 * 必须用 class：它是用 new 调的，箭头函数当不了构造器。
 * 不装则组件走「环境不支持」的兜底分支，那条路径由单独的用例覆盖。
 */
function installIntersectionObserver() {
  const observe = vi.fn()
  let disconnected = false
  const disconnect = vi.fn(() => {
    disconnected = true
  })
  let callback: (entries: { isIntersecting: boolean }[]) => void = () => {}

  class FakeIntersectionObserver {
    constructor(cb: (entries: { isIntersecting: boolean }[]) => void) {
      callback = cb
    }
    observe = observe
    disconnect = disconnect
  }
  vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver)

  return {
    observe,
    disconnect,
    // 真实的 IntersectionObserver 在 disconnect 之后就不再回调，替身要照着来，
    // 否则测出来的「不会重复建实例」是假的
    trigger: (isIntersecting: boolean) => {
      if (disconnected) return
      callback([{ isIntersecting }])
    },
  }
}

describe('AudioPlayer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('默认按可见性懒加载：还没进视口时不建实例、不下载音频', () => {
    installIntersectionObserver()

    const wrapper = mount(AudioPlayer, { props: { audioUrl: 'audio/a.wav' } })

    expect(createMock).not.toHaveBeenCalled()
    expect(waveSurferInstance.load).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('滚进视口后自动建实例并画波形，不需要点播放', async () => {
    const observer = installIntersectionObserver()
    const wrapper = mount(AudioPlayer, { props: { audioUrl: 'audio/a.wav' } })

    observer.trigger(true)
    await wrapper.vm.$nextTick()

    // 这是这条用例的要害：用户一次都没点播放
    expect(createMock).toHaveBeenCalledTimes(1)
    expect(waveSurferInstance.load).toHaveBeenCalledTimes(1)
    // 建完实例就不必再观察
    expect(observer.disconnect).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('建完实例后断开观察，再次进入视口不会重复建', async () => {
    const observer = installIntersectionObserver()
    const wrapper = mount(AudioPlayer, { props: { audioUrl: 'audio/a.wav' } })

    observer.trigger(true)
    await wrapper.vm.$nextTick()
    observer.trigger(true)
    await wrapper.vm.$nextTick()

    expect(createMock).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('环境不支持 IntersectionObserver 时直接初始化，不让波形空着', () => {
    // 此时全局没有 IntersectionObserver（installIntersectionObserver 未调用）
    const wrapper = mount(AudioPlayer, { props: { audioUrl: 'audio/a.wav' } })

    expect(createMock).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('卸载时断开视口观察', () => {
    const observer = installIntersectionObserver()
    const wrapper = mount(AudioPlayer, { props: { audioUrl: 'audio/a.wav' } })

    wrapper.unmount()

    expect(observer.disconnect).toHaveBeenCalled()
  })

  it('eager 时挂载即建实例并加载音频', () => {
    const wrapper = mount(AudioPlayer, { props: { audioUrl: 'audio/a.wav', eager: true } })
    expect(createMock).toHaveBeenCalledTimes(1)
    expect(waveSurferInstance.load).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('波形颜色传的是实际色值，不是 canvas 解析不了的 CSS 变量', () => {
    const wrapper = mount(AudioPlayer, { props: { audioUrl: 'audio/a.wav', eager: true } })

    const options = createMock.mock.calls[0]?.[0] ?? {}
    const waveColor = String(options.waveColor)
    const progressColor = String(options.progressColor)
    expect(waveColor).not.toContain('var(')
    expect(progressColor).not.toContain('var(')
    expect(waveColor).toMatch(/^#|^rgb/)
    expect(progressColor).toMatch(/^#|^rgb/)

    wrapper.unmount()
  })
})

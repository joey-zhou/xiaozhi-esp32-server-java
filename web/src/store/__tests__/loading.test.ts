import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// 全局 setup 把 vue-i18n 的 createI18n 也 mock 了，真实 locales 模块在测试里无法求值
vi.mock('@/locales', () => ({
  i18n: { global: { t: (key: string) => key } },
}))

import { useLoadingStore } from '../loading'

describe('useLoadingStore', () => {
  let store: ReturnType<typeof useLoadingStore>

  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    store = useLoadingStore()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('快速操作在延迟展示期内结束，遮罩全程不出现', () => {
    store.showLoading('A')
    vi.advanceTimersByTime(100)
    store.hideLoading()

    expect(store.isLoading).toBe(false)
    // 就算把原本排期的展示定时器放过去，也不应该补显示
    vi.advanceTimersByTime(1000)
    expect(store.isLoading).toBe(false)
  })

  it('两个快速的并发请求都在延迟展示期内结束，遮罩不出现', () => {
    store.showLoading('A')
    vi.advanceTimersByTime(100)
    store.showLoading('B')
    vi.advanceTimersByTime(50)
    store.hideLoading()
    vi.advanceTimersByTime(50)
    store.hideLoading()

    expect(store.isLoading).toBe(false)
    vi.advanceTimersByTime(1000)
    expect(store.isLoading).toBe(false)
  })

  it('慢操作过了延迟展示期后挂出遮罩，且应用最低显示时间防闪烁', () => {
    store.showLoading('A')

    // 延迟展示期内遮罩还不可见
    vi.advanceTimersByTime(200)
    expect(store.isLoading).toBe(false)

    // 到达延迟展示时间，遮罩显示
    vi.advanceTimersByTime(50)
    expect(store.isLoading).toBe(true)

    // 请求这时就结束了，但还没到最低显示时间，遮罩继续显示
    store.hideLoading()
    expect(store.isLoading).toBe(true)

    vi.advanceTimersByTime(199)
    expect(store.isLoading).toBe(true)

    vi.advanceTimersByTime(1)
    expect(store.isLoading).toBe(false)
  })

  it('延迟展示期内的并发请求共用同一个展示定时器，不会被二次调用延后', () => {
    store.showLoading('A')
    vi.advanceTimersByTime(200)
    store.showLoading('B')

    // 从第一次 showLoading 起满 250ms 应准时显示，不会因为第二次调用被推迟
    vi.advanceTimersByTime(50)
    expect(store.isLoading).toBe(true)
  })

  it('并发请求按计数配对，最后一个结束才隐藏', () => {
    store.showLoading('A')
    store.showLoading('B')
    vi.advanceTimersByTime(250)
    expect(store.isLoading).toBe(true)

    store.hideLoading()
    vi.advanceTimersByTime(2000)
    expect(store.isLoading).toBe(true)

    store.hideLoading()
    vi.advanceTimersByTime(2000)
    expect(store.isLoading).toBe(false)
  })

  it('未传文案时用 common.loading', () => {
    store.showLoading()
    expect(store.loadingText).toBe('common.loading')
  })

  it('待隐藏期间再次 showLoading，遗留的隐藏定时器不会把新遮罩掐掉', () => {
    store.showLoading('A')
    vi.advanceTimersByTime(250)
    expect(store.isLoading).toBe(true)

    store.hideLoading()
    // 距 A 的隐藏定时器触发还有 100ms 时开始操作 B
    vi.advanceTimersByTime(100)
    store.showLoading('B')

    vi.advanceTimersByTime(5000)
    expect(store.isLoading).toBe(true)
    expect(store.loadingText).toBe('B')

    store.hideLoading()
    vi.advanceTimersByTime(2000)
    expect(store.isLoading).toBe(false)
  })
})

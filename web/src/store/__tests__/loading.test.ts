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

  it('并发请求按计数配对，最后一个结束才隐藏', () => {
    store.showLoading('A')
    store.showLoading('B')
    expect(store.isLoading).toBe(true)

    store.hideLoading()
    vi.advanceTimersByTime(2000)
    expect(store.isLoading).toBe(true)

    store.hideLoading()
    vi.advanceTimersByTime(2000)
    expect(store.isLoading).toBe(false)
  })

  it('显示不足最低时间时延迟隐藏', () => {
    store.showLoading()
    vi.advanceTimersByTime(100)
    store.hideLoading()

    expect(store.isLoading).toBe(true)
    vi.advanceTimersByTime(200)
    expect(store.isLoading).toBe(false)
  })

  it('未传文案时用 common.loading', () => {
    store.showLoading()
    expect(store.loadingText).toBe('common.loading')
  })

  it('待隐藏期间再次 showLoading，遗留定时器不会把新遮罩掐掉', () => {
    store.showLoading('A')
    vi.advanceTimersByTime(100)
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

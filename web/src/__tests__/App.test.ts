import { nextTick } from 'vue'
import { shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import dayjs from 'dayjs'
// 中文语言包由 main.ts 侧引入，测试里单独引一次
import 'dayjs/locale/zh-cn'

// 用真实 ref 承载语言，watch 才能收到变化
vi.mock('@/composables/useLocale', async () => {
  const { ref } = await import('vue')
  const currentLocale = ref<'zh-CN' | 'en-US'>('zh-CN')
  return {
    useLocale: () => ({ currentLocale, antdLocale: { locale: 'zh-cn' } }),
  }
})

import App from '../App.vue'
import { useLocale } from '@/composables/useLocale'

const { currentLocale } = useLocale()

describe('App 的 dayjs 语言同步', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    currentLocale.value = 'zh-CN'
    dayjs.locale('en')
  })

  it('挂载时按当前界面语言设置 dayjs', () => {
    shallowMount(App)

    expect(dayjs.locale()).toBe('zh-cn')
  })

  it('切到英文时同步改 dayjs', async () => {
    shallowMount(App)

    currentLocale.value = 'en-US'
    await nextTick()

    expect(dayjs.locale()).toBe('en')
  })
})

import { useStorage } from '@vueuse/core'
import { computed, watch } from 'vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import enUS from 'ant-design-vue/es/locale/en_US'
import type { Locale } from 'ant-design-vue/es/locale'
import { i18n } from '@/locales'

export type LocaleType = 'zh-CN' | 'en-US'

// 语言配置映射
const localeMap: Record<LocaleType, Locale> = {
  'zh-CN': zhCN,
  'en-US': enUS,
}

// 语言显示名称
const localeNames: Record<LocaleType, string> = {
  'zh-CN': '简体中文',
  'en-US': 'English',
}

export function useLocale() {
  const currentLocale = useStorage<LocaleType>('locale', 'zh-CN')

  // 获取 Ant Design Vue 的 locale 对象
  const antdLocale = computed(() => localeMap[currentLocale.value])

  // 获取当前语言的显示名称
  const localeName = computed(() => localeNames[currentLocale.value])

  // 切换语言
  const toggleLocale = () => {
    currentLocale.value = currentLocale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  }

  // 设置特定语言
  const setLocale = (locale: LocaleType) => {
    currentLocale.value = locale
  }

  // 监听语言变化，同步到 i18n
  watch(
    currentLocale,
    (newLocale) => {
      // 同步更新 i18n 实例
      if (i18n && i18n.global) {
        i18n.global.locale.value = newLocale
      }
    },
    { immediate: true }
  )

  // 获取所有可用语言
  const availableLocales = Object.keys(localeMap) as LocaleType[]

  return {
    currentLocale,
    antdLocale,
    localeName,
    toggleLocale,
    setLocale,
    availableLocales,
    localeNames,
  }
}

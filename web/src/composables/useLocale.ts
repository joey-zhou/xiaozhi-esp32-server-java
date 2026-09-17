import { useStorage } from '@vueuse/core'
import { computed, watch } from 'vue'
import { ConfigProvider, message } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import enUS from 'ant-design-vue/es/locale/en_US'
import type { Locale } from 'ant-design-vue/es/locale'
import { i18n, ensureLocaleLoaded, type SupportedLocale } from '@/locales'
import { STORAGE_LOCALE } from '@/constants/storage'

export type LocaleType = SupportedLocale

// 语言配置映射
const localeMap: Record<LocaleType, Locale> = {
  'zh-CN': zhCN,
  'en-US': enUS,
}

// ConfigProvider.config 的 TS 声明只认旧版几个色值字段，运行时其实是把整个 params 浅合并进
// 内部的 globalConfigForApi，静态方法内部套的 <ConfigProvider> 直接读它，与组件树里那个走同一条渲染路径
type ConfigProviderConfig = (params: { locale?: Locale }) => void

function syncStaticMethodsLocale(locale: Locale) {
  ;(ConfigProvider.config as unknown as ConfigProviderConfig)({ locale })
}

// 语言显示名称
const localeNames: Record<LocaleType, string> = {
  'zh-CN': '简体中文',
  'en-US': 'English',
}

// 语言是全局唯一的一份状态，放模块作用域：放在函数体里的话，每个调用 useLocale() 的组件
// 都会注册一个 watch，切一次语言就重复跑一遍语言包加载、加载失败还会弹两条提示
const currentLocale = useStorage<LocaleType>(STORAGE_LOCALE, 'zh-CN')

// 获取 Ant Design Vue 的 locale 对象
const antdLocale = computed(() => localeMap[currentLocale.value])

// 获取当前语言的显示名称
const localeName = computed(() => localeNames[currentLocale.value])

// 获取所有可用语言
const availableLocales = Object.keys(localeMap) as LocaleType[]

// 监听语言变化，同步到 i18n
// 语言包按需异步加载：先确保目标语言的文案已就位，再切 i18n 的 locale，
// 避免出现"已经切到新语言、但文案还没到"而短暂显示裸 key 的情况
watch(
  currentLocale,
  async (newLocale) => {
    // Modal.confirm / message 这类静态方法挂在 <a-config-provider> 组件树之外，
    // 拿不到它的 locale，只能靠 ConfigProvider.config() 写入的全局配置；
    // antd 的 locale 对象是静态打进来的，不用等语言包加载完
    syncStaticMethodsLocale(localeMap[newLocale])

    const loaded = await ensureLocaleLoaded(newLocale)
    if (!loaded) {
      // 加载失败就留在当前语言，不做一次没有文案支撑的切换
      message.error(i18n.global.t('common.loadFailed'))
      return
    }
    if (i18n && i18n.global) {
      i18n.global.locale.value = newLocale
    }
    // index.html 里只有静态默认值，切语言后要跟着改，屏幕阅读器与浏览器翻译都读这个
    document.documentElement.lang = newLocale
  },
  { immediate: true }
)

export function useLocale() {
  // 切换语言
  const toggleLocale = () => {
    currentLocale.value = currentLocale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  }

  // 设置特定语言
  const setLocale = (locale: LocaleType) => {
    currentLocale.value = locale
  }

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

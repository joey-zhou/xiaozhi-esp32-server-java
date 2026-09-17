/**
 * 国际化配置
 * 语言选择的持久化键与 composables/useLocale.ts 共用 STORAGE_LOCALE，两边不要各写各的
 *
 * zh-CN.ts / en-US.ts 两份语言包各有 1400+ 行，静态引入会把用不到的另一份也打进首屏入口 chunk，
 * 这里只同步创建 i18n 实例、异步按需加载文案：启动时只拉当前语言，切换语言时才拉另一份
 */
import { createI18n } from 'vue-i18n'
import { STORAGE_LOCALE } from '@/constants/storage'

export type SupportedLocale = 'zh-CN' | 'en-US'

const DEFAULT_LOCALE: SupportedLocale = 'zh-CN'

/** 持久化的语言选择；取不到或值不合法都退回默认语言 */
function readStoredLocale(): SupportedLocale {
  const stored = localStorage.getItem(STORAGE_LOCALE)
  return stored === 'en-US' ? 'en-US' : DEFAULT_LOCALE
}

const initialLocale = readStoredLocale()

// 创建 i18n 实例：messages 先留空，由 ensureLocaleLoaded 异步填充
export const i18n = createI18n({
  legacy: false, // 使用 Composition API 模式
  locale: initialLocale,
  fallbackLocale: DEFAULT_LOCALE,
  messages: {},
})

/** 语言包的消息结构，以 zh-CN.ts 的实际导出为准，en-US.ts 靠 locale-parity 测试保证 key 集合一致 */
type LocaleMessages = (typeof import('./zh-CN'))['default']

/** 每种语言各自的动态加载函数，只有真正用到时才会触发对应的网络请求 */
const localeLoaders: Record<SupportedLocale, () => Promise<{ default: LocaleMessages }>> = {
  'zh-CN': () => import('./zh-CN'),
  'en-US': () => import('./en-US'),
}

const loadedLocales = new Set<SupportedLocale>()
const pendingLoads = new Map<SupportedLocale, Promise<boolean>>()

/**
 * 确保某个语言包的文案已经加载进 i18n 实例，返回是否加载成功。
 * 重复调用只会真正加载一次：加载中共用同一个 promise，加载成功后直接短路。
 */
export function ensureLocaleLoaded(locale: SupportedLocale): Promise<boolean> {
  if (loadedLocales.has(locale)) {
    return Promise.resolve(true)
  }

  let pending = pendingLoads.get(locale)
  if (!pending) {
    pending = localeLoaders[locale]()
      .then((mod) => {
        i18n.global.setLocaleMessage(locale, mod.default)
        loadedLocales.add(locale)
        return true
      })
      .catch((error) => {
        console.error(`加载语言包 ${locale} 失败`, error)
        // 允许下一次调用重新尝试加载，而不是把失败结果长期缓存住
        pendingLoads.delete(locale)
        return false
      })
    pendingLoads.set(locale, pending)
  }
  return pending
}

// 应用启动必须先有一份可用的文案，main.ts 会等它落地后再挂载，避免首屏闪一下裸 key
export const i18nReady = ensureLocaleLoaded(initialLocale)

// 导出 t 函数，方便在 JS/TS 中使用
export const { t } = i18n.global

export default i18n

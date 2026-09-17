/**
 * 国际化配置
 * 语言选择的持久化键与 composables/useLocale.ts 共用 STORAGE_LOCALE，两边不要各写各的
 */
import { createI18n } from 'vue-i18n'
import { STORAGE_LOCALE } from '@/constants/storage'
import zhCN from './zh-CN'
import enUS from './en-US'

// 默认语言
const defaultLocale = localStorage.getItem(STORAGE_LOCALE) || 'zh-CN'

// 创建 i18n 实例
export const i18n = createI18n({
  legacy: false, // 使用 Composition API 模式
  locale: defaultLocale,
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

// 导出 t 函数，方便在 JS/TS 中使用
export const { t } = i18n.global

export default i18n


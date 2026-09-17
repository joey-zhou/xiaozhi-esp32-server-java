/**
 * 日期时间工具函数
 * 全站唯一的时间格式化入口，视图里不要再写内联实现
 */
import { i18n } from '@/locales'

/** 可接受的时间入参：ISO 字符串、时间戳、Date，空值一律走 defaultValue */
export type DateInput = string | number | Date | null

function toDate(value?: DateInput): Date | null {
  if (value === undefined || value === null || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 取当前语言，交给 toLocale* 决定日期书写顺序 */
function currentLocale(): string {
  return i18n.global.locale.value
}

/**
 * 格式化为本地日期字符串
 * @param value - 时间值
 * @param defaultValue - 空值或非法值时的返回
 */
export function formatDate(value?: DateInput, defaultValue: string = '-'): string {
  const date = toDate(value)
  if (!date) return defaultValue
  return date.toLocaleDateString(currentLocale())
}

/**
 * 格式化为本地日期时间字符串
 * @param value - 时间值
 * @param defaultValue - 空值或非法值时的返回
 */
export function formatDateTime(value?: DateInput, defaultValue: string = '-'): string {
  const date = toDate(value)
  if (!date) return defaultValue
  return date.toLocaleString(currentLocale())
}

/**
 * 格式化为「月-日 时:分」，用于会话列表这类只需要区分近期条目的场景
 * @param value - 时间值
 * @param defaultValue - 空值或非法值时的返回
 */
export function formatShortDateTime(value?: DateInput, defaultValue: string = '-'): string {
  const date = toDate(value)
  if (!date) return defaultValue
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${date.getMonth() + 1}-${date.getDate()} ${hours}:${minutes}`
}

/**
 * 格式化为「yyyy-MM-dd HH:mm:ss」，与后端 @JsonFormat 的书写一致，不随语言变化
 * @param value - 时间值
 * @param defaultValue - 空值或非法值时的返回
 */
export function formatBackendDateTime(value?: DateInput, defaultValue: string = '-'): string {
  const date = toDate(value)
  if (!date) return defaultValue
  const pad = (n: number) => String(n).padStart(2, '0')
  const ymd = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  return `${ymd} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/**
 * 相对时间描述，超过 7 天回退为日期
 * @param value - 时间值
 * @param defaultValue - 空值或非法值时的返回
 */
export function getRelativeTime(value?: DateInput, defaultValue: string = '-'): string {
  const date = toDate(value)
  if (!date) return defaultValue

  const seconds = Math.floor((Date.now() - date.getTime()) / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) return i18n.global.t('time.justNow')
  if (minutes < 60) return i18n.global.t('time.minutesAgo', { count: minutes })
  if (hours < 24) return i18n.global.t('time.hoursAgo', { count: hours })
  if (days < 7) return i18n.global.t('time.daysAgo', { count: days })

  return formatDate(date, defaultValue)
}

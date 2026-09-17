import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// 全局 setup 把 vue-i18n 的 createI18n 也 mock 了，真实 locales 模块在测试里无法求值
vi.mock('@/locales', () => ({
  i18n: {
    global: {
      locale: { value: 'zh-CN' },
      t: (key: string, params?: Record<string, unknown>) =>
        params ? `${key}:${JSON.stringify(params)}` : key,
    },
  },
}))

import {
  formatBackendDateTime,
  formatClockTime,
  formatDate,
  formatDateTime,
  formatShortDateTime,
  getRelativeTime,
} from '../date'

describe('formatDate', () => {
  it('返回默认值 "-" 当输入为空', () => {
    expect(formatDate()).toBe('-')
    expect(formatDate(undefined)).toBe('-')
    expect(formatDate('')).toBe('-')
  })

  it('自定义默认值', () => {
    expect(formatDate(undefined, 'N/A')).toBe('N/A')
    expect(formatDate('', '暂无')).toBe('暂无')
  })

  it('非法日期串按默认值处理，不返回 Invalid Date', () => {
    expect(formatDate('不是日期')).toBe('-')
    expect(formatDateTime('不是日期')).toBe('-')
  })

  it('格式化有效日期字符串', () => {
    const result = formatDate('2026-03-12')
    // toLocaleDateString 输出因环境而异，只验证返回了非默认值
    expect(result).not.toBe('-')
    expect(typeof result).toBe('string')
  })

  it('接受时间戳与 Date 对象', () => {
    const date = new Date('2026-03-12T10:30:00')
    expect(formatDate(date)).toBe(formatDate(date.getTime()))
  })
})

describe('formatDateTime', () => {
  it('返回默认值 "-" 当输入为空', () => {
    expect(formatDateTime()).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDateTime('')).toBe('-')
  })

  it('自定义默认值', () => {
    expect(formatDateTime(undefined, 'N/A')).toBe('N/A')
  })

  it('格式化有效日期时间字符串', () => {
    const result = formatDateTime('2026-03-12T10:30:00')
    expect(result).not.toBe('-')
    expect(typeof result).toBe('string')
  })
})

describe('formatShortDateTime', () => {
  it('输出「月-日 时:分」且时分补零', () => {
    expect(formatShortDateTime('2026-03-12T09:05:00')).toBe('3-12 09:05')
    expect(formatShortDateTime('2026-12-01T22:30:00')).toBe('12-1 22:30')
  })

  it('空值与非法值走默认值', () => {
    expect(formatShortDateTime()).toBe('-')
    expect(formatShortDateTime('不是日期', 'N/A')).toBe('N/A')
  })
})

describe('formatBackendDateTime', () => {
  it('输出 yyyy-MM-dd HH:mm:ss 且不随语言变化', () => {
    expect(formatBackendDateTime('2026-03-12T09:05:07')).toBe('2026-03-12 09:05:07')
  })

  it('空值与非法值走默认值', () => {
    expect(formatBackendDateTime()).toBe('-')
    expect(formatBackendDateTime('不是日期')).toBe('-')
  })
})

describe('formatClockTime', () => {
  it('输出 HH:mm:ss 且各段补零', () => {
    expect(formatClockTime('2026-03-12T09:05:07')).toBe('09:05:07')
    expect(formatClockTime('2026-03-12T23:00:00')).toBe('23:00:00')
  })

  it('不传参数时取当前时刻', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-12T08:03:04'))
    expect(formatClockTime()).toBe('08:03:04')
    vi.useRealTimers()
  })

  it('非法值走默认值', () => {
    expect(formatClockTime('不是日期')).toBe('-')
    expect(formatClockTime('不是日期', 'N/A')).toBe('N/A')
  })
})

describe('getRelativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-12T12:00:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('返回 "-" 当输入为空', () => {
    expect(getRelativeTime()).toBe('-')
    expect(getRelativeTime(undefined)).toBe('-')
    expect(getRelativeTime('')).toBe('-')
  })

  // 四档文案全部走 i18n，不能再出现硬编码中文
  it('时间差小于60秒走 time.justNow', () => {
    expect(getRelativeTime(new Date('2026-03-12T11:59:30'))).toBe('time.justNow')
  })

  it('时间差小于60分钟走 time.minutesAgo 并带 count', () => {
    expect(getRelativeTime(new Date('2026-03-12T11:55:00'))).toBe('time.minutesAgo:{"count":5}')
    expect(getRelativeTime(new Date('2026-03-12T11:30:00'))).toBe('time.minutesAgo:{"count":30}')
  })

  it('时间差小于24小时走 time.hoursAgo 并带 count', () => {
    expect(getRelativeTime(new Date('2026-03-12T10:00:00'))).toBe('time.hoursAgo:{"count":2}')
  })

  it('时间差小于7天走 time.daysAgo 并带 count', () => {
    expect(getRelativeTime(new Date('2026-03-09T12:00:00'))).toBe('time.daysAgo:{"count":3}')
  })

  it('超过7天回退到日期格式', () => {
    const result = getRelativeTime(new Date('2026-03-02T12:00:00'))
    expect(result).not.toContain('time.')
    expect(result).not.toBe('-')
  })
})

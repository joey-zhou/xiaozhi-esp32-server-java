import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { formatDate, formatDateTime, getRelativeTime } from '../date'

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

  it('格式化有效日期字符串', () => {
    const result = formatDate('2026-03-12')
    // toLocaleDateString 输出因环境而异，只验证返回了非默认值
    expect(result).not.toBe('-')
    expect(typeof result).toBe('string')
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

  it('返回 "刚刚" 当时间差小于60秒', () => {
    const now = new Date('2026-03-12T11:59:30')
    expect(getRelativeTime(now.toISOString())).toBe('刚刚')
  })

  it('返回 "N分钟前" 当时间差小于60分钟', () => {
    const fiveMinAgo = new Date('2026-03-12T11:55:00')
    expect(getRelativeTime(fiveMinAgo.toISOString())).toBe('5分钟前')

    const thirtyMinAgo = new Date('2026-03-12T11:30:00')
    expect(getRelativeTime(thirtyMinAgo.toISOString())).toBe('30分钟前')
  })

  it('返回 "N小时前" 当时间差小于24小时', () => {
    const twoHoursAgo = new Date('2026-03-12T10:00:00')
    expect(getRelativeTime(twoHoursAgo.toISOString())).toBe('2小时前')
  })

  it('返回 "N天前" 当时间差小于7天', () => {
    const threeDaysAgo = new Date('2026-03-09T12:00:00')
    expect(getRelativeTime(threeDaysAgo.toISOString())).toBe('3天前')
  })

  it('返回格式化日期 当时间差>=7天', () => {
    const tenDaysAgo = new Date('2026-03-02T12:00:00')
    const result = getRelativeTime(tenDaysAgo.toISOString())
    // 超过7天应回退到 formatDate, 非 "天前" 格式
    expect(result).not.toContain('天前')
    expect(result).not.toBe('-')
  })
})

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  timeFix,
  welcome,
  formatDate,
  formatDateTime,
  formatNumber,
  formatDuration,
} from '../time'
import dayjs from 'dayjs'

describe('timeFix', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  const cases: [number, string][] = [
    [0, '凌晨好'],
    [3, '凌晨好'],
    [5, '凌晨好'],
    [6, '早上好'],
    [8, '早上好'],
    [9, '上午好'],
    [11, '上午好'],
    [12, '中午好'],
    [13, '中午好'],
    [14, '下午好'],
    [16, '下午好'],
    [17, '傍晚好'],
    [18, '傍晚好'],
    [19, '晚上好'],
    [21, '晚上好'],
    [22, '夜里好'],
    [23, '夜里好'],
  ]

  it.each(cases)('hour=%i 返回 "%s"', (hour, expected) => {
    vi.spyOn(dayjs.prototype, 'hour').mockReturnValue(hour)
    expect(timeFix()).toBe(expected)
  })
})

describe('welcome', () => {
  it('返回一个字符串', () => {
    const result = welcome()
    expect(typeof result).toBe('string')
    expect(result.length).toBeGreaterThan(0)
  })

  it('返回值在预定义列表中', () => {
    const validMessages = [
      '祝你开心每一天',
      '今天又是元气满满的一天',
      '愿你心想事成',
      '保持好心情',
      '每天进步一点点',
      '加油！你是最棒的',
    ]
    // 多次调用确保随机值在范围内
    for (let i = 0; i < 20; i++) {
      expect(validMessages).toContain(welcome())
    }
  })
})

describe('formatDate', () => {
  it('格式化为 YYYY-MM-DD', () => {
    expect(formatDate('2026-03-12T10:30:00')).toBe('2026-03-12')
    expect(formatDate(new Date('2026-01-01T00:00:00'))).toBe('2026-01-01')
  })
})

describe('formatDateTime', () => {
  it('格式化为 YYYY-MM-DD HH:mm:ss', () => {
    expect(formatDateTime('2026-03-12T10:30:45')).toBe('2026-03-12 10:30:45')
  })
})

describe('formatNumber', () => {
  it('添加千分位分隔符', () => {
    expect(formatNumber(1234567)).toBe('1,234,567')
    expect(formatNumber(1000)).toBe('1,000')
    expect(formatNumber(100)).toBe('100')
  })

  it('返回 "0" 当值为 0 或 falsy', () => {
    expect(formatNumber(0)).toBe('0')
  })
})

describe('formatDuration', () => {
  it('返回 "0秒" 当值为 0 或 falsy', () => {
    expect(formatDuration(0)).toBe('0秒')
  })

  it('格式化纯秒数', () => {
    expect(formatDuration(30)).toBe('30.0秒')
    expect(formatDuration(0.5)).toBe('0.5秒')
  })

  it('格式化包含分钟的时长', () => {
    expect(formatDuration(90)).toBe('1分30.0秒')
    expect(formatDuration(125)).toBe('2分5.0秒')
    expect(formatDuration(60)).toBe('1分0.0秒')
  })
})

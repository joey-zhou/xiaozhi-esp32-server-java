import { describe, it, expect } from 'vitest'
import {
  formatCompact,
  formatDecimal,
  formatMilliseconds,
  formatPercentage,
  formatBytes,
} from '../format'

describe('formatCompact', () => {
  it('返回 "0" 当值为 undefined', () => {
    expect(formatCompact(undefined)).toBe('0')
  })

  it('返回 "0" 当值为 null', () => {
    expect(formatCompact(null as unknown as undefined)).toBe('0')
  })

  it('返回 "0" 当值为 0', () => {
    expect(formatCompact(0)).toBe('0')
  })

  it('格式化百万级数值为 M', () => {
    expect(formatCompact(1000000)).toBe('1.0M')
    expect(formatCompact(2500000)).toBe('2.5M')
    expect(formatCompact(10000000)).toBe('10.0M')
  })

  it('格式化千级数值为 K', () => {
    expect(formatCompact(1000)).toBe('1.0K')
    expect(formatCompact(1500)).toBe('1.5K')
    expect(formatCompact(999999)).toBe('1000.0K')
  })

  it('小于1000的数值直接显示整数', () => {
    expect(formatCompact(1)).toBe('1')
    expect(formatCompact(999)).toBe('999')
    expect(formatCompact(42)).toBe('42')
  })

  it('处理负数', () => {
    expect(formatCompact(-1500)).toBe('-1.5K')
    expect(formatCompact(-2500000)).toBe('-2.5M')
    expect(formatCompact(-42)).toBe('-42')
  })
})

describe('formatDecimal', () => {
  it('返回 "--" 当值为 undefined', () => {
    expect(formatDecimal(undefined)).toBe('--')
  })

  it('返回 "--" 当值为 null', () => {
    expect(formatDecimal(null as unknown as undefined)).toBe('--')
  })

  it('返回 "--" 当值为 NaN', () => {
    expect(formatDecimal(NaN)).toBe('--')
  })

  it('默认保留1位小数', () => {
    expect(formatDecimal(3.14159)).toBe('3.1')
    expect(formatDecimal(0)).toBe('0.0')
  })

  it('可指定小数位数', () => {
    expect(formatDecimal(3.14159, 2)).toBe('3.14')
    expect(formatDecimal(3.14159, 0)).toBe('3')
    expect(formatDecimal(3.14159, 4)).toBe('3.1416')
  })
})

describe('formatMilliseconds', () => {
  it('返回 "--" 当值为 undefined', () => {
    expect(formatMilliseconds(undefined)).toBe('--')
  })

  it('返回 "--" 当值为 NaN', () => {
    expect(formatMilliseconds(NaN)).toBe('--')
  })

  it('默认无小数位', () => {
    expect(formatMilliseconds(123.456)).toBe('123 ms')
    expect(formatMilliseconds(0)).toBe('0 ms')
  })

  it('可指定小数位数', () => {
    expect(formatMilliseconds(123.456, 2)).toBe('123.46 ms')
    expect(formatMilliseconds(123.456, 1)).toBe('123.5 ms')
  })
})

describe('formatPercentage', () => {
  it('返回 "--" 当值为 undefined', () => {
    expect(formatPercentage(undefined)).toBe('--')
  })

  it('返回 "--" 当值为 NaN', () => {
    expect(formatPercentage(NaN)).toBe('--')
  })

  it('将小数转换为百分比', () => {
    expect(formatPercentage(0.5)).toBe('50.0%')
    expect(formatPercentage(1)).toBe('100.0%')
    expect(formatPercentage(0)).toBe('0.0%')
    expect(formatPercentage(0.123)).toBe('12.3%')
  })

  it('可指定小数位数', () => {
    expect(formatPercentage(0.12345, 2)).toBe('12.35%')
    expect(formatPercentage(0.12345, 0)).toBe('12%')
  })
})

describe('formatBytes', () => {
  it('返回 "0 B" 当值为 0 或 falsy', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(undefined)).toBe('0 B')
  })

  it('格式化字节', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(1)).toBe('1 B')
  })

  it('格式化 KB', () => {
    expect(formatBytes(1024)).toBe('1.0 KB')
    expect(formatBytes(1536)).toBe('1.5 KB')
  })

  it('格式化 MB', () => {
    expect(formatBytes(1048576)).toBe('1.0 MB')
    expect(formatBytes(1572864)).toBe('1.5 MB')
  })

  it('格式化 GB', () => {
    expect(formatBytes(1073741824)).toBe('1.00 GB')
    expect(formatBytes(2684354560)).toBe('2.50 GB')
  })

  it('处理负数（取绝对值）', () => {
    expect(formatBytes(-1024)).toBe('1.0 KB')
  })
})

/**
 * 数值格式化
 *
 * 边界取值的两条规矩，不要混用：
 * - 计数类（formatCompact / formatBytes）无值即 0，直接按 0 渲染
 * - 度量类（formatDecimal / formatMilliseconds / formatPercentage）分不清「0」和「没采到」，一律给 '--'
 */

function isBlank(value?: number): boolean {
  return value === undefined || value === null || Number.isNaN(value)
}

/** 大数缩写为 K / M，保留符号 */
export function formatCompact(value?: number): string {
  if (isBlank(value)) return '0'
  const num = Number(value)
  const abs = Math.abs(num)
  if (abs >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (abs >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toFixed(0)
}

export function formatDecimal(value?: number, digits = 1): string {
  if (isBlank(value)) return '--'
  return Number(value).toFixed(digits)
}

export function formatMilliseconds(value?: number, digits = 0): string {
  if (isBlank(value)) return '--'
  return `${Number(value).toFixed(digits)} ms`
}

export function formatPercentage(value?: number, digits = 1): string {
  if (isBlank(value)) return '--'
  return `${(Number(value) * 100).toFixed(digits)}%`
}

/** 字节数转可读单位，保留符号：负值只可能来自脏数据，抹成正数会把问题藏起来 */
export function formatBytes(value?: number): string {
  if (isBlank(value)) return '0 B'
  const num = Number(value)
  const abs = Math.abs(num)
  const sign = num < 0 ? '-' : ''
  if (abs < 1024) return `${sign}${abs.toFixed(0)} B`
  if (abs < 1024 * 1024) return `${sign}${(abs / 1024).toFixed(1)} KB`
  if (abs < 1024 * 1024 * 1024) return `${sign}${(abs / (1024 * 1024)).toFixed(1)} MB`
  return `${sign}${(abs / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

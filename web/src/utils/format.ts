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

/**
 * 格式化耗时，按量级自动在 ms / s 间切换单位，用于 STT/LLM/TTS、GC 暂停这类跨度较大的耗时展示
 * - 空值、NaN 一律返回 defaultValue；0 也按 defaultValue 处理——这类耗时指标的 0 基本来自
 *   “还没有样本”时上游的兜底默认值，不是真测出来的零耗时，显示成 "0 ms" 会误导成瞬间完成
 * - 量级按绝对值判断、符号保留在结果里：<1ms 保留 2 位小数，1ms~1000ms 取整，≥1000ms 换算成秒保留 2 位小数
 * @param value - 毫秒数
 * @param defaultValue - 空值/0 时的返回，默认 '--'
 */
export function formatDuration(value?: number, defaultValue: string = '--'): string {
  if (isBlank(value) || Number(value) === 0) return defaultValue
  const ms = Number(value)
  const abs = Math.abs(ms)
  if (abs < 1) return `${ms.toFixed(2)} ms`
  if (abs < 1000) return `${ms.toFixed(0)} ms`
  return `${(ms / 1000).toFixed(2)} s`
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

/**
 * 把秒数格式化为 "分:秒" 时长，用于录音计时、音频进度条这类场景
 * - 空值、NaN、负数一律按 0 秒处理，不产出 NaN 或负数字样
 * - 小数部分直接舍去；分钟数不封顶，超过 99 分钟就显示三位及以上数字，不改变格式
 * @param seconds - 总秒数
 */
export function formatSecondsToClock(seconds?: number): string {
  const total = isBlank(seconds) || Number(seconds) < 0 ? 0 : Math.floor(Number(seconds))
  const minutes = Math.floor(total / 60)
  const remainingSeconds = total % 60
  return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`
}

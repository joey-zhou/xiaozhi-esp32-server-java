/**
 * 时间相关工具函数
 */
import dayjs from 'dayjs'

/**
 * 根据时间返回问候语
 */
export function timeFix(): string {
  const hour = dayjs().hour()
  if (hour < 6) {
    return '凌晨好'
  } else if (hour < 9) {
    return '早上好'
  } else if (hour < 12) {
    return '上午好'
  } else if (hour < 14) {
    return '中午好'
  } else if (hour < 17) {
    return '下午好'
  } else if (hour < 19) {
    return '傍晚好'
  } else if (hour < 22) {
    return '晚上好'
  } else {
    return '夜里好'
  }
}

/**
 * 返回欢迎语
 */
export function welcome(): string {
  const welcomeMessages = [
    '祝你开心每一天',
    '今天又是元气满满的一天',
    '愿你心想事成',
    '保持好心情',
    '每天进步一点点',
    '加油！你是最棒的',
  ]
  const index = Math.floor(Math.random() * welcomeMessages.length)
  return welcomeMessages[index] as string
}

/**
 * 格式化日期（YYYY-MM-DD）
 */
export function formatDate(date: string | Date): string {
  return dayjs(date).format('YYYY-MM-DD')
}

/**
 * 格式化时间（YYYY-MM-DD HH:mm:ss）
 */
export function formatDateTime(date: string | Date): string {
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss')
}

/**
 * 格式化数字（添加千分位）
 */
export function formatNumber(num: number): string {
  return num ? num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',') : '0'
}

/**
 * 格式化时长（秒转换为分秒）
 */
export function formatDuration(seconds: number): string {
  if (!seconds) return '0秒'
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = (seconds % 60).toFixed(1)
  return minutes > 0 ? `${minutes}分${remainingSeconds}秒` : `${remainingSeconds}秒`
}


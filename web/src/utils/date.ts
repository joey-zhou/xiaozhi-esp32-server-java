/**
 * 日期时间工具函数
 */

/**
 * 格式化日期为本地日期字符串
 * @param dateString - ISO日期字符串
 * @param defaultValue - 默认值
 * @returns 格式化后的日期字符串
 */
export function formatDate(dateString?: string, defaultValue: string = '-'): string {
  if (!dateString) return defaultValue
  try {
    return new Date(dateString).toLocaleDateString()
  } catch (error) {
    console.error('日期格式化失败:', error)
    return defaultValue
  }
}

/**
 * 格式化日期时间为本地日期时间字符串
 * @param dateString - ISO日期字符串
 * @param defaultValue - 默认值
 * @returns 格式化后的日期时间字符串
 */
export function formatDateTime(dateString?: string, defaultValue: string = '-'): string {
  if (!dateString) return defaultValue
  try {
    return new Date(dateString).toLocaleString()
  } catch (error) {
    console.error('日期时间格式化失败:', error)
    return defaultValue
  }
}

/**
 * 获取相对时间描述（如：刚刚、5分钟前、1小时前）
 * @param dateString - ISO日期字符串
 * @returns 相对时间描述
 */
export function getRelativeTime(dateString?: string): string {
  if (!dateString) return '-'
  
  try {
    const now = Date.now()
    const date = new Date(dateString).getTime()
    const diff = now - date
    
    const seconds = Math.floor(diff / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    const days = Math.floor(hours / 24)
    
    if (seconds < 60) return '刚刚'
    if (minutes < 60) return `${minutes}分钟前`
    if (hours < 24) return `${hours}小时前`
    if (days < 7) return `${days}天前`
    
    return formatDate(dateString)
  } catch (error) {
    console.error('相对时间计算失败:', error)
    return '-'
  }
}


/**
 * 资源 URL 处理工具
 */

/**
 * 把后端返回的资源路径拼成可访问的 URL
 * 未配置 VITE_BACKEND_URL 时返回相对路径，由同源或反向代理承载，
 * 不做 localhost 兜底，避免生产漏配时静默把全站图片音频指到本机
 */
export function getResourceUrl(path?: string): string | undefined {
  if (!path) return undefined

  // 如果已经是完整URL，直接返回
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path
  }

  // 去掉开头的斜杠，拼接时统一补一个
  const relativePath = path.replace(/^\/+/, '')

  // 后端地址尾部的斜杠要剥掉，否则拼出双斜杠
  const backendUrl = (import.meta.env.VITE_BACKEND_URL || '').replace(/\/+$/, '')

  if (backendUrl) {
    return `${backendUrl}/${relativePath}`
  }

  // 未配置后端地址时走相对路径
  return `/${relativePath}`
}

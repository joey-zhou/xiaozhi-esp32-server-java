/**
 * 资源 URL 处理工具
 */

/**
 * 获取资源URL（处理相对路径）
 * 参考 Vue2 项目的实现
 */
export function getResourceUrl(path?: string): string | undefined {
  if (!path) return undefined
  
  // 如果已经是完整URL，直接返回
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path
  }
  
  // 确保URL以/开头
  if (!path.startsWith('/')) {
    path = '/' + path
  }
  
  // 开发环境下，需要使用完整的后端地址
  if (import.meta.env.DEV) {
    // 开发环境下，我们需要指定后端地址
    const backendUrl = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8091'
    
    // 移除开头的斜杠，因为我们要将完整的URL传给组件
    if (path.startsWith('/')) {
      path = path.substring(1)
    }
    
    // 构建完整的URL
    return `${backendUrl}/${path}`
  }
  
  // 生产环境下，直接使用相对路径，由Nginx代理处理
  return path
}

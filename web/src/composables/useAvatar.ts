/**
 * 头像处理 Composable
 */
import { getResourceUrl } from '@/utils/resource'

export function useAvatar() {
  /**
   * 获取头像URL
   * 使用统一的资源URL处理逻辑
   */
  function getAvatarUrl(avatar?: string): string | undefined {
    return getResourceUrl(avatar)
  }

  return {
    getAvatarUrl,
  }
}



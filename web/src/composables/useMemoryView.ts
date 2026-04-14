import { useRouter } from 'vue-router'
import { ROUTES } from '@/router/routes'

export interface MemoryViewParams {
  roleId?: number
  deviceId?: string
}

/**
 * 记忆管理视图跳转 Composable
 * 提供统一的方法来跳转到记忆管理页面
 * 支持传入 roleId 和可选的 deviceId
 */
export function useMemoryView() {
  const router = useRouter()

  /**
   * 跳转到记忆管理页面
   * @param params 查询参数：roleId（必填）和 deviceId（可选）
   */
  const navigateToMemory = (params: MemoryViewParams) => {
    const query: Record<string, string> = {}

    if (params.roleId !== undefined) {
      query.roleId = String(params.roleId)
    }

    if (params.deviceId !== undefined) {
      query.deviceId = params.deviceId
    }

    router.push({
      path: ROUTES.MEMORY_CHAT,
      query
    })
  }

  return {
    navigateToMemory
  }
}

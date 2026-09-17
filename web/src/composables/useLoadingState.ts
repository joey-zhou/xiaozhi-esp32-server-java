import { reactive } from 'vue'

/**
 * Loading 状态管理 Composable
 * 用于管理多个加载状态，避免 loading 状态混乱
 */

export interface UseLoadingStateOptions {
  /**
   * 初始加载状态
   */
  initialStates?: Record<string, boolean>
}

export function useLoadingState(options: UseLoadingStateOptions = {}) {
  // 加载状态集合
  const loadingStates = reactive<Record<string, boolean>>(
    options.initialStates || {}
  )

  /**
   * 判断是否正在加载
   */
  const isLoading = (key: string): boolean => {
    return loadingStates[key] || false
  }

  /**
   * 包装异步函数，自动管理加载状态
   */
  const withLoading = async <T>(
    key: string,
    fn: () => Promise<T>
  ): Promise<T> => {
    loadingStates[key] = true
    try {
      return await fn()
    } finally {
      loadingStates[key] = false
    }
  }

  return {
    isLoading,
    withLoading
  }
}

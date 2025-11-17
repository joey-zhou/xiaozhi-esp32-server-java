import { reactive, computed } from 'vue'

/**
 * Loading 状态管理 Composable
 * 用于管理多个加载状态，避免 loading 状态混乱
 */

export interface UseLoadingStateOptions {
  /**
   * 初始加载状态
   */
  initialStates?: Record<string, boolean>
  
  /**
   * 全局加载回调
   */
  onLoadingChange?: (key: string, loading: boolean) => void
}

export function useLoadingState(options: UseLoadingStateOptions = {}) {
  // 加载状态集合
  const loadingStates = reactive<Record<string, boolean>>(
    options.initialStates || {}
  )
  
  // 是否有任何加载中的状态
  const isAnyLoading = computed(() => {
    return Object.values(loadingStates).some(loading => loading)
  })
  
  // 是否全部加载中
  const isAllLoading = computed(() => {
    const keys = Object.keys(loadingStates)
    if (keys.length === 0) return false
    return keys.every(key => loadingStates[key])
  })
  
  // 加载中的数量
  const loadingCount = computed(() => {
    return Object.values(loadingStates).filter(loading => loading).length
  })
  
  // 加载中的 key 列表
  const loadingKeys = computed(() => {
    return Object.keys(loadingStates).filter(key => loadingStates[key])
  })
  
  /**
   * 设置加载状态
   */
  const setLoading = (key: string, loading: boolean) => {
    loadingStates[key] = loading
    options.onLoadingChange?.(key, loading)
  }
  
  /**
   * 开始加载
   */
  const startLoading = (key: string) => {
    setLoading(key, true)
  }
  
  /**
   * 结束加载
   */
  const stopLoading = (key: string) => {
    setLoading(key, false)
  }
  
  /**
   * 切换加载状态
   */
  const toggleLoading = (key: string) => {
    setLoading(key, !loadingStates[key])
  }
  
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
    startLoading(key)
    try {
      return await fn()
    } finally {
      stopLoading(key)
    }
  }
  
  /**
   * 批量设置加载状态
   */
  const setLoadingBatch = (states: Record<string, boolean>) => {
    Object.entries(states).forEach(([key, loading]) => {
      setLoading(key, loading)
    })
  }
  
  /**
   * 重置所有加载状态
   */
  const resetAll = () => {
    Object.keys(loadingStates).forEach(key => {
      loadingStates[key] = false
    })
  }
  
  /**
   * 清除指定的加载状态
   */
  const clear = (key: string) => {
    delete loadingStates[key]
  }
  
  /**
   * 清除所有加载状态
   */
  const clearAll = () => {
    Object.keys(loadingStates).forEach(key => {
      delete loadingStates[key]
    })
  }
  
  /**
   * 创建命名空间的加载管理器（用于组件内部多个加载状态）
   */
  const createNamespace = (namespace: string) => {
    const getKey = (key: string) => `${namespace}:${key}`
    
    return {
      isLoading: (key: string) => isLoading(getKey(key)),
      setLoading: (key: string, loading: boolean) => setLoading(getKey(key), loading),
      startLoading: (key: string) => startLoading(getKey(key)),
      stopLoading: (key: string) => stopLoading(getKey(key)),
      toggleLoading: (key: string) => toggleLoading(getKey(key)),
      withLoading: <T>(key: string, fn: () => Promise<T>) => withLoading(getKey(key), fn),
      resetAll: () => {
        Object.keys(loadingStates)
          .filter(k => k.startsWith(`${namespace}:`))
          .forEach(k => setLoading(k, false))
      },
      clearAll: () => {
        Object.keys(loadingStates)
          .filter(k => k.startsWith(`${namespace}:`))
          .forEach(k => clear(k))
      }
    }
  }
  
  /**
   * 创建加载状态的 computed（用于组合使用）
   */
  const createLoadingComputed = (...keys: string[]) => {
    return computed(() => keys.some(key => isLoading(key)))
  }
  
  /**
   * 等待所有指定的加载完成
   */
  const waitForAll = async (...keys: string[]): Promise<void> => {
    return new Promise((resolve) => {
      const checkInterval = setInterval(() => {
        if (!keys.some(key => isLoading(key))) {
          clearInterval(checkInterval)
          resolve()
        }
      }, 100)
    })
  }
  
  return {
    // 状态
    loadingStates,
    isAnyLoading,
    isAllLoading,
    loadingCount,
    loadingKeys,
    
    // 方法
    setLoading,
    startLoading,
    stopLoading,
    toggleLoading,
    isLoading,
    withLoading,
    setLoadingBatch,
    resetAll,
    clear,
    clearAll,
    createNamespace,
    createLoadingComputed,
    waitForAll
  }
}


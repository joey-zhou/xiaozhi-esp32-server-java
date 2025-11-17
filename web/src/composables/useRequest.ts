/**
 * 请求处理 Composable
 * 集成全局 Loading、错误处理、防抖等功能
 */
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useLoadingStore } from '@/store/loading'
import { useDebounceFn } from '@vueuse/core'

interface RequestOptions<T = unknown> {
  showLoading?: boolean // 是否显示全局 loading
  loadingText?: string // loading 文本
  showError?: boolean // 是否显示错误提示
  showSuccess?: boolean // 是否显示成功提示
  successText?: string // 成功提示文本
  onSuccess?: (data: T) => void // 成功回调
  onError?: (error: Error) => void // 错误回调
}

// 错误类型守卫
function isErrorWithMessage(error: unknown): error is { message: string } {
  return typeof error === 'object' && error !== null && 'message' in error
}

function isErrorWithCode(error: unknown): error is { code: string } {
  return typeof error === 'object' && error !== null && 'code' in error
}

function getErrorMessage(error: unknown): string {
  if (isErrorWithMessage(error)) {
    return error.message
  }
  if (typeof error === 'string') {
    return error
  }
  return '操作失败'
}

/**
 * 请求处理 Hook
 */
export function useRequest() {
  const loadingStore = useLoadingStore()
  const loading = ref(false)

  /**
   * 执行请求
   */
  const execute = async <T = unknown>(
    requestFn: () => Promise<T>,
    options: RequestOptions<T> = {}
  ): Promise<T | undefined> => {
    const {
      showLoading = false,
      loadingText = '加载中...',
      showError = true,
      showSuccess = false,
      successText = '操作成功',
      onSuccess,
      onError,
    } = options

    try {
      loading.value = true
      if (showLoading) {
        loadingStore.showLoading(loadingText)
      }

      const result = await requestFn()

      if (showSuccess) {
        message.success(successText)
      }

      if (onSuccess) {
        onSuccess(result)
      }

      return result
    } catch (error: unknown) {
      // 忽略请求取消错误
      if (isErrorWithCode(error) && (error.code === 'ERR_CANCELED' || 
          (isErrorWithMessage(error) && (error.message.includes('canceled') || error.message.includes('aborted'))))) {
        console.debug('请求已取消:', getErrorMessage(error))
        return undefined
      }

      console.error('Request error:', error)

      if (showError) {
        const errorMessage = getErrorMessage(error)
        message.error(errorMessage)
      }

      if (onError && error instanceof Error) {
        onError(error)
      }

      return undefined
    } finally {
      loading.value = false
      if (showLoading) {
        loadingStore.hideLoading()
      }
    }
  }

  /**
   * 创建防抖请求函数
   */
  const createDebouncedRequest = <T = unknown>(
    requestFn: () => Promise<T>,
    delay = 500,
    options: RequestOptions<T> = {}
  ) => {
    return useDebounceFn(() => execute(requestFn, options), delay)
  }

  return {
    loading,
    execute,
    createDebouncedRequest,
  }
}

/**
 * 简化的请求执行器（直接使用，不需要返回值）
 */
export async function withLoading<T = unknown>(
  requestFn: () => Promise<T>,
  loadingText = '加载中...'
): Promise<T | undefined> {
  const loadingStore = useLoadingStore()

  try {
    loadingStore.showLoading(loadingText)
    return await requestFn()
  } catch (error: unknown) {
    // 忽略请求取消错误
    if (isErrorWithCode(error) && (error.code === 'ERR_CANCELED' || 
        (isErrorWithMessage(error) && (error.message.includes('canceled') || error.message.includes('aborted'))))) {
      console.debug('请求已取消:', getErrorMessage(error))
      return undefined
    }
    
    console.error('Request error:', error)
    const errorMessage = getErrorMessage(error)
    message.error(errorMessage)
    return undefined
  } finally {
    loadingStore.hideLoading()
  }
}

/**
 * 错误处理包装器
 */
export async function withErrorHandler<T = unknown>(
  requestFn: () => Promise<T>,
  errorMessage = '操作失败'
): Promise<T | undefined> {
  try {
    return await requestFn()
  } catch (error: unknown) {
    // 忽略请求取消错误
    if (isErrorWithCode(error) && (error.code === 'ERR_CANCELED' || 
        (isErrorWithMessage(error) && (error.message.includes('canceled') || error.message.includes('aborted'))))) {
      console.debug('请求已取消:', getErrorMessage(error))
      return undefined
    }
    
    console.error('Error:', error)
    const msg = getErrorMessage(error) || errorMessage
    message.error(msg)
    return undefined
  }
}


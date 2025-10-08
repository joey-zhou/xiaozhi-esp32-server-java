/**
 * 请求处理 Composable
 * 集成全局 Loading、错误处理、防抖等功能
 */
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useLoadingStore } from '@/store/loading'
import { useDebounceFn } from '@vueuse/core'

interface RequestOptions {
  showLoading?: boolean // 是否显示全局 loading
  loadingText?: string // loading 文本
  showError?: boolean // 是否显示错误提示
  showSuccess?: boolean // 是否显示成功提示
  successText?: string // 成功提示文本
  onSuccess?: (data: any) => void // 成功回调
  onError?: (error: any) => void // 错误回调
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
  const execute = async <T = any>(
    requestFn: () => Promise<T>,
    options: RequestOptions = {}
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
    } catch (error: any) {
      // 忽略请求取消错误
      if (error?.code === 'ERR_CANCELED' || error?.message?.includes('canceled') || error?.message?.includes('aborted')) {
        console.debug('请求已取消:', error.message)
        return undefined
      }

      console.error('Request error:', error)

      if (showError) {
        const errorMessage = error?.message || error?.response?.data?.message || '操作失败'
        message.error(errorMessage)
      }

      if (onError) {
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
  const createDebouncedRequest = <T = any>(
    requestFn: () => Promise<T>,
    delay = 500,
    options: RequestOptions = {}
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
export async function withLoading<T = any>(
  requestFn: () => Promise<T>,
  loadingText = '加载中...'
): Promise<T | undefined> {
  const loadingStore = useLoadingStore()

  try {
    loadingStore.showLoading(loadingText)
    return await requestFn()
  } catch (error: any) {
    // 忽略请求取消错误
    if (error?.code === 'ERR_CANCELED' || error?.message?.includes('canceled') || error?.message?.includes('aborted')) {
      console.debug('请求已取消:', error.message)
      return undefined
    }
    
    console.error('Request error:', error)
    const errorMessage = error?.message || error?.response?.data?.message || '操作失败'
    message.error(errorMessage)
    return undefined
  } finally {
    loadingStore.hideLoading()
  }
}

/**
 * 错误处理包装器
 */
export async function withErrorHandler<T = any>(
  requestFn: () => Promise<T>,
  errorMessage = '操作失败'
): Promise<T | undefined> {
  try {
    return await requestFn()
  } catch (error: any) {
    // 忽略请求取消错误
    if (error?.code === 'ERR_CANCELED' || error?.message?.includes('canceled') || error?.message?.includes('aborted')) {
      console.debug('请求已取消:', error.message)
      return undefined
    }
    
    console.error('Error:', error)
    const msg = error?.message || error?.response?.data?.message || errorMessage
    message.error(msg)
    return undefined
  }
}

